package com.petrolpump.discount.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OCR via Gemini. Concurrent calls are limited (target ~3–5 in-flight) with
 * short retries on 429/503 — free/paid Gemini quotas fail hard under burst load.
 */
@Service
public class GeminiBillOcrService {
    private static final Logger log = LoggerFactory.getLogger(GeminiBillOcrService.class);
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    );
    private static final int MAX_IN_FLIGHT = 5;
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final Semaphore slots = new Semaphore(MAX_IN_FLIGHT, true);
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(MAX_IN_FLIGHT + 2);

    public GeminiBillOcrService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-flash-latest}") String model) {
        this.mapper = new ObjectMapper();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(httpExecutor)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(55));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public BillOcrResult extract(MultipartFile image) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bill reading is temporarily unavailable. Please try again shortly.");
        }

        boolean acquired;
        try {
            acquired = slots.tryAcquire(45, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Upload interrupted — please try again.");
        }
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Many bills are being read right now. Wait a few seconds and try again.");
        }

        long t0 = System.currentTimeMillis();
        try {
            byte[] compressed = ImageCompressUtil.toJpegBytes(image.getBytes());
            log.info("OCR start size={}kb inFlight≈{}", compressed.length / 1024,
                    MAX_IN_FLIGHT - slots.availablePermits());
            BillOcrResult result = callWithRetries(compressed);
            log.info("OCR ok in {}ms", System.currentTimeMillis() - t0);
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OCR failed after {}ms: {}", System.currentTimeMillis() - t0, ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not read the bill photo. Take a clearer photo and try again.");
        } finally {
            slots.release();
        }
    }

    private BillOcrResult callWithRetries(byte[] jpegBytes) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callOnce(jpegBytes);
            } catch (RestClientResponseException ex) {
                last = ex;
                int code = ex.getStatusCode().value();
                boolean retryable = code == 429 || code == 503 || code == 500;
                log.warn("Gemini HTTP {} attempt {}/{}", code, attempt, MAX_ATTEMPTS);
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleepBackoff(attempt, ex);
            } catch (Exception ex) {
                last = ex;
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                boolean retryable = msg.contains("429") || msg.contains("resource_exhausted")
                        || msg.contains("unavailable") || msg.contains("timed out") || msg.contains("timeout");
                log.warn("Gemini error attempt {}/{}: {}", attempt, MAX_ATTEMPTS, ex.toString());
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleepBackoff(attempt, null);
            }
        }
        if (last instanceof RestClientResponseException rex && rex.getStatusCode().value() == 429) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bill reading is busy. Wait a few seconds and try again.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not read the bill photo. Take a clearer photo and try again.");
    }

    private BillOcrResult callOnce(byte[] jpegBytes) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(jpegBytes);
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
    }

    private static void sleepBackoff(int attempt, RestClientResponseException ex) {
        long ms = 400L * attempt * attempt;
        if (ex != null) {
            String ra = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
            if (ra != null) {
                try {
                    ms = Math.max(ms, Long.parseLong(ra.trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                    /* ignore */
                }
            }
        }
        try {
            Thread.sleep(Math.min(ms, 8000L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
