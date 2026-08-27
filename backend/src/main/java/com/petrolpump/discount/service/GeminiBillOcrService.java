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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gemini OCR (paid tier recommended). Free-tier keys return 429 when quota is gone.
 * After quota 429 we open a circuit so uploads skip straight to Vision/local.
 */
@Service
public class GeminiBillOcrService {
    private static final Logger log = LoggerFactory.getLogger(GeminiBillOcrService.class);
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    );
    private static final int MAX_IN_FLIGHT = 5;
    private static final int MAX_ATTEMPTS = 2;
    /** Skip Gemini for this long after billing/quota 429. */
    private static final long CIRCUIT_OPEN_MS = 15 * 60 * 1000L;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final Semaphore slots = new Semaphore(MAX_IN_FLIGHT, true);
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(MAX_IN_FLIGHT + 2);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public GeminiBillOcrService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.5-flash-lite}") String model) {
        this.mapper = new ObjectMapper();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(httpExecutor)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(35));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    public void openCircuit(String reason) {
        long until = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
        circuitOpenUntil.set(until);
        log.warn("Gemini circuit OPEN for {}min reason={}", CIRCUIT_OPEN_MS / 60000, reason);
    }

    private void closeCircuitOnSuccess() {
        if (circuitOpenUntil.get() != 0) {
            circuitOpenUntil.set(0);
            log.info("Gemini circuit CLOSED — requests succeeding again");
        }
    }

    /** Multipart entry (gemini-only mode). */
    public BillOcrResult extract(MultipartFile image) {
        try {
            String mime = image.getContentType() == null ? "image/jpeg" : image.getContentType().toLowerCase();
            if (mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();
            if (!ALLOWED_MIME.contains(mime) && !mime.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads allowed");
            }
            byte[] compressed = ImageCompressUtil.toJpegBytes(image.getBytes());
            return extractBytes(compressed);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not process image");
        }
    }

    public BillOcrResult extractBytes(byte[] jpegBytes) {
        return withSlot(() -> callWithRetries(jpegBytes));
    }

    /** Single Gemini call — used by auto-fallback so we don't burn time on 429 retries. */
    public BillOcrResult extractBytesOnce(byte[] jpegBytes) {
        if (isCircuitOpen()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini circuit open");
        }
        return withSlot(() -> {
            try {
                BillOcrResult r = callOnce(jpegBytes);
                closeCircuitOnSuccess();
                return r;
            } catch (RestClientResponseException ex) {
                int code = ex.getStatusCode().value();
                String body = ex.getResponseBodyAsString();
                log.warn("Gemini HTTP {} bodySnippet={}", code, snippet(body));
                if (code == 429) {
                    openCircuit("HTTP 429");
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini quota exceeded");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini OCR failed");
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Gemini error: {}", ex.toString());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini OCR failed");
            }
        });
    }

    private BillOcrResult withSlot(OcrCall call) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini not configured");
        }
        if (isCircuitOpen()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini circuit open");
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(12, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Upload interrupted");
        }
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bill reading is busy");
        }
        long t0 = System.currentTimeMillis();
        try {
            log.info("Gemini OCR slot acquired inFlight≈{}", MAX_IN_FLIGHT - slots.availablePermits());
            BillOcrResult r = call.run();
            log.info("OCR ok provider=gemini in {}ms", System.currentTimeMillis() - t0);
            return r;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Gemini OCR failed: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini OCR failed");
        } finally {
            slots.release();
        }
    }

    private BillOcrResult callWithRetries(byte[] jpegBytes) {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                BillOcrResult r = callOnce(jpegBytes);
                closeCircuitOnSuccess();
                return r;
            } catch (RestClientResponseException ex) {
                last = ex;
                int code = ex.getStatusCode().value();
                log.warn("Gemini HTTP {} attempt {}/{} snippet={}", code, attempt, MAX_ATTEMPTS,
                        snippet(ex.getResponseBodyAsString()));
                if (code == 429) {
                    openCircuit("HTTP 429");
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini quota exceeded");
                }
                if (attempt == MAX_ATTEMPTS || (code != 500 && code != 503)) {
                    break;
                }
                sleep(300L * attempt);
            } catch (Exception ex) {
                last = ex;
                log.warn("Gemini error attempt {}/{}: {}", attempt, MAX_ATTEMPTS, ex.toString());
                if (attempt == MAX_ATTEMPTS) break;
                sleep(300L * attempt);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                last == null ? "Gemini OCR failed" : "Gemini OCR failed");
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 180 ? t.substring(0, 180) : t;
    }

    private static String textOrNull(JsonNode json, String field) {
        JsonNode n = json.get(field);
        if (n == null || n.isNull()) return null;
        String v = n.asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    @FunctionalInterface
    private interface OcrCall {
        BillOcrResult run() throws Exception;
    }
}
