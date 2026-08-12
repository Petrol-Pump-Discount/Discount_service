package com.petrolpump.discount.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiBillOcrService {
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GeminiBillOcrService(
            ObjectMapper mapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.0-flash}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public BillOcrResult extract(MultipartFile image) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini API key not configured. Set GEMINI_API_KEY / app.gemini.api-key");
        }
        try {
            String mime = image.getContentType() == null ? "image/jpeg" : image.getContentType();
            String b64 = Base64.getEncoder().encodeToString(image.getBytes());
            String prompt = """
                    You are reading an Indian petrol pump thermal receipt photo (IndianOil / SiteOmat style).
                    Return ONLY compact JSON with keys:
                    duplicate (boolean: true if text contains "Duplicate Receipt Copy" OR bill/receipt number ends with -DUPLT),
                    billNo (string),
                    fccId (string or empty),
                    transId (string or empty; Trns.ID / Transaction ID),
                    vehicleNo (string; use empty if NotEntered / blank),
                    volumeLitres (number),
                    saleAmount (number),
                    fuel (string).
                    Prefer FCC ID when present. Do not invent values; use empty string/null if unreadable.
                    """;

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(
                                    Map.of("text", prompt),
                                    Map.of("inline_data", Map.of("mime_type", mime, "data", b64))
                            )
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"
                    )
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;

            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("Empty Gemini response");
            }
            String text = parts.get(0).path("text").asText();
            JsonNode json = mapper.readTree(stripFences(text));

            BillOcrResult out = new BillOcrResult();
            out.setDuplicate(json.path("duplicate").asBoolean(false));
            out.setBillNo(textOrNull(json, "billNo"));
            out.setFccId(textOrNull(json, "fccId"));
            out.setTransId(textOrNull(json, "transId"));
            out.setVehicleNo(textOrNull(json, "vehicleNo"));
            if (json.hasNonNull("volumeLitres")) {
                out.setVolumeLitres(json.get("volumeLitres").asDouble());
            }
            if (json.hasNonNull("saleAmount")) {
                out.setSaleAmount(json.get("saleAmount").asDouble());
            }
            out.setFuel(textOrNull(json, "fuel"));
            out.setRawText(text);
            return out;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini OCR failed: " + ex.getMessage());
        }
    }

    private static String textOrNull(JsonNode json, String field) {
        JsonNode n = json.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String v = n.asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}
