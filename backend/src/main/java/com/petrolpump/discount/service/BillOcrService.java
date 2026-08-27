package com.petrolpump.discount.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * OCR router for production pump load (target ~3–5 QPS):
 * <ol>
 *   <li>Gemini Flash-Lite (cheap paid) — structured JSON</li>
 *   <li>Google Cloud Vision DOCUMENT_TEXT_DETECTION — reliable text + regex</li>
 *   <li>Local Tesseract — last resort</li>
 * </ol>
 * Free-tier Gemini will never sustain real traffic; enable billing on the API key.
 */
@Service
public class BillOcrService {
    private static final Logger log = LoggerFactory.getLogger(BillOcrService.class);

    private final GeminiBillOcrService gemini;
    private final GoogleVisionOcrService vision;
    private final LocalReceiptOcrService local;
    private final String mode;

    public BillOcrService(
            GeminiBillOcrService gemini,
            GoogleVisionOcrService vision,
            LocalReceiptOcrService local,
            @Value("${app.ocr.mode:auto}") String mode) {
        this.gemini = gemini;
        this.vision = vision;
        this.local = local;
        this.mode = mode == null ? "auto" : mode.trim().toLowerCase();
    }

    public BillOcrResult extract(MultipartFile image) {
        byte[] jpeg;
        try {
            jpeg = ImageCompressUtil.toJpegBytes(image.getBytes());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not process image");
        }

        log.info("OCR start size={}kb mode={} geminiCircuit={} vision={}",
                Math.max(1, jpeg.length / 1024), mode, gemini.isCircuitOpen(), vision.isConfigured());

        if ("tesseract".equals(mode) || "local".equals(mode)) {
            return requireUsable(extractLocal(jpeg), "tesseract");
        }
        if ("vision".equals(mode)) {
            return requireUsable(vision.extractBytes(jpeg), "vision");
        }
        if ("gemini".equals(mode)) {
            return requireUsable(gemini.extractBytes(jpeg), "gemini");
        }

        // auto: Gemini → Vision → Tesseract
        if (gemini.isConfigured() && !gemini.isCircuitOpen()) {
            try {
                BillOcrResult r = gemini.extractBytesOnce(jpeg);
                if (usable(r)) {
                    log.info("OCR provider=gemini");
                    return r;
                }
                log.warn("Gemini OCR incomplete fields — trying next provider");
            } catch (ResponseStatusException ex) {
                log.warn("Gemini unavailable ({}) — trying next provider", ex.getStatusCode().value());
            } catch (Exception ex) {
                log.warn("Gemini error — trying next provider: {}", ex.toString());
            }
        } else if (gemini.isCircuitOpen()) {
            log.info("Skipping Gemini (circuit open) — trying Vision/local");
        }

        if (vision.isConfigured()) {
            try {
                BillOcrResult r = vision.extractBytes(jpeg);
                if (usable(r)) {
                    log.info("OCR provider=vision");
                    return r;
                }
                log.warn("Vision OCR incomplete fields fcc={} vol={} snippet={}",
                        r.getFccId(), r.getVolumeLitres(), snippet(r.getRawText()));
            } catch (ResponseStatusException ex) {
                log.warn("Vision unavailable ({}) — trying local", ex.getStatusCode().value());
            } catch (Exception ex) {
                log.warn("Vision error — trying local: {}", ex.toString());
            }
        }

        if (local.isAvailable()) {
            BillOcrResult r = extractLocal(jpeg);
            return requireUsable(r, "tesseract");
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not read the bill photo. Enable Gemini billing or Vision API, then retry.");
    }

    private BillOcrResult extractLocal(byte[] jpeg) {
        try {
            BillOcrResult r = local.extract(jpeg);
            if (!usable(r)) {
                log.warn("Tesseract incomplete fcc={} vol={} snippet={}",
                        r.getFccId(), r.getVolumeLitres(), snippet(r.getRawText()));
            }
            return r;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Local OCR failed: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not read the bill photo. Take a clearer photo and try again.");
        }
    }

    private static BillOcrResult requireUsable(BillOcrResult r, String provider) {
        if (!usable(r)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read FCC ID / volume from bill. Take a clearer photo.");
        }
        log.info("OCR provider={}", provider);
        return r;
    }

    private static boolean usable(BillOcrResult r) {
        if (r == null) return false;
        boolean hasId = !r.candidateReceiptKeys().isEmpty();
        boolean hasVol = r.getVolumeLitres() != null && r.getVolumeLitres() > 0;
        return hasId && hasVol;
    }

    private static String snippet(String text) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 220 ? t.substring(0, 220) : t;
    }
}
