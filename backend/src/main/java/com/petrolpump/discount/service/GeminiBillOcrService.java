package com.petrolpump.discount.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeminiBillOcrService {
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    );

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GeminiBillOcrService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-flash-latest}") String model) {
        this.mapper = new ObjectMapper();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder().requestFactory(factory).build();
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
            String mime = image.getContentType() == null ? "image/jpeg" : image.getContentType().toLowerCase();
            if (mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();
            if (!ALLOWED_MIME.contains(mime) && !mime.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads allowed");
            }
            byte[] compressed = ImageCompressUtil.toJpegBytes(image.getBytes());
            String b64 = Base64.getEncoder().encodeToString(compressed);
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
                                    Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", b64))
                            )
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"
                    )
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent";

            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-goog-api-key", apiKey)
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
