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
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Paid Google Cloud Vision DOCUMENT_TEXT_DETECTION — reliable thermal-receipt OCR.
 * ~$1.50 / 1,000 images. Use as fallback when Gemini quota is exhausted.
 */
@Service
public class GoogleVisionOcrService {
    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOcrService.class);
    private static final int MAX_IN_FLIGHT = 5;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final Semaphore slots = new Semaphore(MAX_IN_FLIGHT, true);
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(MAX_IN_FLIGHT + 2);

    public GoogleVisionOcrService(@Value("${app.vision.api-key:}") String apiKey) {
        this.mapper = new ObjectMapper();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .executor(httpExecutor)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public BillOcrResult extractBytes(byte[] jpegBytes) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vision OCR not configured");
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(15, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Upload interrupted");
        }
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bill reading is busy");
        }
        long t0 = System.currentTimeMillis();
        try {
            String text = detectText(jpegBytes);
            log.info("OCR ok provider=vision chars={} in {}ms", text.length(), System.currentTimeMillis() - t0);
            return LocalReceiptOcrService.parse(text);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Vision HTTP {} snippet={}", ex.getStatusCode().value(), snippet(ex.getResponseBodyAsString()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vision OCR failed");
        } catch (Exception ex) {
            log.warn("Vision OCR error: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vision OCR failed");
        } finally {
            slots.release();
        }
    }

    private String detectText(byte[] jpegBytes) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(jpegBytes);
        Map<String, Object> body = Map.of(
                "requests", List.of(Map.of(
                        "image", Map.of("content", b64),
                        "features", List.of(Map.of("type", "DOCUMENT_TEXT_DETECTION", "maxResults", 1))
                ))
        );
        String response = restClient.post()
                .uri("https://vision.googleapis.com/v1/images:annotate?key={key}", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode root = mapper.readTree(response);
        JsonNode err = root.path("responses").path(0).path("error");
        if (!err.isMissingNode() && err.has("message")) {
            throw new IllegalStateException(err.path("message").asText("Vision error"));
        }
        String full = root.path("responses").path(0).path("fullTextAnnotation").path("text").asText("");
        if (full.isBlank()) {
            JsonNode annotations = root.path("responses").path(0).path("textAnnotations");
            if (annotations.isArray() && !annotations.isEmpty()) {
                full = annotations.get(0).path("description").asText("");
            }
        }
        return full == null ? "" : full;
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 180 ? t.substring(0, 180) : t;
    }
}
