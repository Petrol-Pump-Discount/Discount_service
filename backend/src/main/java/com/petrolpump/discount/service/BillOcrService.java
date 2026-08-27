package com.petrolpump.discount.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * OCR router: Gemini when quota allows, otherwise fast local Tesseract.
 * Real-world pumps need uploads to work even when Google returns 429.
 */
@Service
public class BillOcrService {
    private static final Logger log = LoggerFactory.getLogger(BillOcrService.class);

    private final GeminiBillOcrService gemini;
    private final LocalReceiptOcrService local;
    private final String mode;

    public BillOcrService(
            GeminiBillOcrService gemini,
            LocalReceiptOcrService local,
            @Value("${app.ocr.mode:auto}") String mode) {
        this.gemini = gemini;
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

        log.info("OCR start size={}kb mode={}", Math.max(1, jpeg.length / 1024), mode);

        if ("tesseract".equals(mode) || "local".equals(mode)) {
            return extractLocal(jpeg);
        }
        if ("gemini".equals(mode)) {
            return gemini.extractBytes(jpeg);
        }

        // auto: Gemini first (1 attempt); on 429/fail → local
        if (gemini.isConfigured()) {
            try {
                BillOcrResult r = gemini.extractBytesOnce(jpeg);
                if (usable(r)) {
                    log.info("OCR provider=gemini");
                    return r;
                }
                log.warn("Gemini OCR incomplete fields — trying local");
            } catch (ResponseStatusException ex) {
                log.warn("Gemini unavailable ({}) — falling back to local OCR", ex.getStatusCode().value());
            } catch (Exception ex) {
                log.warn("Gemini error — falling back to local OCR: {}", ex.toString());
            }
        }

        if (local.isAvailable()) {
            BillOcrResult r = extractLocal(jpeg);
            log.info("OCR provider=tesseract");
            return r;
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not read the bill photo. Take a clearer photo and try again.");
    }

    private BillOcrResult extractLocal(byte[] jpeg) {
        try {
            BillOcrResult r = local.extract(jpeg);
            if (!usable(r)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not read FCC ID / volume from bill. Take a clearer photo.");
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

    private static boolean usable(BillOcrResult r) {
        if (r == null) return false;
        boolean hasId = !r.candidateReceiptKeys().isEmpty();
        boolean hasVol = r.getVolumeLitres() != null && r.getVolumeLitres() > 0;
        return hasId && hasVol;
    }
}
