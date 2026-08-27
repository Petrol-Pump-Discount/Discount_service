package com.petrolpump.discount.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast local OCR for IndianOil / SiteOmat thermal bills.
 * Used when Gemini is rate-limited (429) so the pump keeps working under load.
 */
@Service
public class LocalReceiptOcrService {
    private static final Logger log = LoggerFactory.getLogger(LocalReceiptOcrService.class);

    private static final Pattern FCC = Pattern.compile(
            "(?i)(?:FCC\\s*I[D1]|FCCID|F\\s*C\\s*C\\s*I\\s*[D1])\\s*[:.\\-]?\\s*([0-9]{6,12})");
    private static final Pattern TRANS = Pattern.compile(
            "(?i)(?:Trns\\.?\\s*I[D1]|Trans(?:action)?\\s*I[D1]|Txn\\s*I[D1])\\s*[:.\\-]?\\s*([0-9]{6,12})");
    private static final Pattern BILL = Pattern.compile(
            "(?i)(?:Bill\\s*No\\.?|Receipt\\s*No\\.?|Bill\\s*#)\\s*[:.\\-]?\\s*([A-Z0-9\\-]{3,20})");
    private static final Pattern VOLUME = Pattern.compile(
            "(?i)(?:Volume|Vo[l1](?:ume)?\\s*\\(?L(?:tr)?s?\\)?|Sale\\s*Vol)\\s*[:.\\-]?\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern VOLUME_LITRE = Pattern.compile(
            "(?i)\\b([0-9]+\\.[0-9]{2,3})\\s*(?:L|Ltr|Ltrs?|Litres?)\\b");
    private static final Pattern VOLUME_NEXT_LINE = Pattern.compile(
            "(?i)(?:Volume|Vol)\\s*[:.\\-]?\\s*(?:\\n|\\r\\n?)\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?:Amount|Sale\\s*Amt|Net\\s*Amount|Rs\\.?)\\s*[:.\\-]?\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern VEHICLE = Pattern.compile(
            "(?i)(?:Vehicle\\s*(?:No\\.?|Number|#)?|Veh\\.?\\s*No\\.?)\\s*[:.\\-]?\\s*([A-Z0-9]{6,12}|Not\\s*Entered)");
    private static final Pattern FUEL = Pattern.compile(
            "(?i)\\b(XTRAGREEN|XTRA\\s*GREEN|HSD|MS|PETROL|DIESEL|XP95|XP100)\\b");

    private final Object tessLock = new Object();
    private volatile boolean tessReady;
    private volatile net.sourceforge.tess4j.Tesseract tess;

    public boolean isAvailable() {
        ensureTess();
        return tessReady;
    }

    public BillOcrResult extract(byte[] jpegBytes) {
        ensureTess();
        if (!tessReady || tess == null) {
            throw new IllegalStateException("Local OCR not available");
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (img == null) {
                throw new IllegalStateException("Invalid image");
            }
            BufferedImage prep = preprocess(img);
            String text;
            synchronized (tessLock) {
                text = tess.doOCR(prep);
            }
            if (text == null) text = "";
            log.info("Local OCR chars={}", text.length());
            return parse(text);
        } catch (Exception ex) {
            throw new IllegalStateException("Local OCR failed: " + ex.getMessage(), ex);
        }
    }

    static BillOcrResult parse(String text) {
        BillOcrResult out = new BillOcrResult();
        String norm = text.replace('\u00A0', ' ');
        out.setRawText(norm);
        String upper = norm.toUpperCase(Locale.ROOT);
        out.setDuplicate(upper.contains("DUPLICATE RECEIPT") || upper.contains("-DUPLT") || upper.contains("DUPLT"));

        Matcher m = FCC.matcher(norm);
        if (m.find()) out.setFccId(m.group(1));
        m = TRANS.matcher(norm);
        if (m.find()) out.setTransId(m.group(1));
        m = BILL.matcher(norm);
        if (m.find()) out.setBillNo(m.group(1).replaceAll("\\s+", ""));
        m = VOLUME.matcher(norm);
        if (m.find()) {
            try {
                out.setVolumeLitres(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignored) { /* skip */ }
        }
        if (out.getVolumeLitres() == null) {
            m = VOLUME_NEXT_LINE.matcher(norm);
            if (m.find()) {
                try {
                    out.setVolumeLitres(Double.parseDouble(m.group(1)));
                } catch (NumberFormatException ignored) { /* skip */ }
            }
        }
        if (out.getVolumeLitres() == null) {
            m = VOLUME_LITRE.matcher(norm);
            if (m.find()) {
                try {
                    out.setVolumeLitres(Double.parseDouble(m.group(1)));
                } catch (NumberFormatException ignored) { /* skip */ }
            }
        }
        m = AMOUNT.matcher(norm);
        if (m.find()) {
            try {
                out.setSaleAmount(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignored) { /* skip */ }
        }
        m = VEHICLE.matcher(norm);
        if (m.find()) {
            String v = m.group(1).replaceAll("\\s+", "");
            if (v.equalsIgnoreCase("NotEntered")) out.setVehicleNo("");
            else out.setVehicleNo(v.toUpperCase(Locale.ROOT));
        }
        m = FUEL.matcher(norm);
        if (m.find()) out.setFuel(m.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT));

        // Fallback: long digit runs near FCC-like context if labels OCR poorly
        if (out.getFccId() == null && out.getTransId() == null) {
            Matcher digits = Pattern.compile("\\b([0-9]{9,11})\\b").matcher(norm);
            if (digits.find()) out.setFccId(digits.group(1));
            if (digits.find()) out.setTransId(digits.group(1));
        }
        return out;
    }

    private static BufferedImage preprocess(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        // Upscale small phone shots for Tesseract
        double scale = (w < 1200) ? 1200.0 / w : 1.0;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage gray = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return gray;
    }

    private void ensureTess() {
        if (tessReady || tess != null) return;
        synchronized (tessLock) {
            if (tess != null) return;
            try {
                net.sourceforge.tess4j.Tesseract t = new net.sourceforge.tess4j.Tesseract();
                String dataPath = System.getenv("TESSDATA_PREFIX");
                if (dataPath == null || dataPath.isBlank()) {
                    dataPath = "/usr/share/tesseract-ocr/5/tessdata";
                }
                t.setDatapath(dataPath);
                t.setLanguage("eng");
                t.setPageSegMode(6);
                t.setOcrEngineMode(1);
                // Smoke: datapath must exist
                java.nio.file.Path p = java.nio.file.Path.of(dataPath);
                if (!java.nio.file.Files.isDirectory(p)) {
                    // Debian/Ubuntu package layouts differ
                    if (java.nio.file.Files.isDirectory(java.nio.file.Path.of("/usr/share/tessdata"))) {
                        dataPath = "/usr/share/tessdata";
                        t.setDatapath(dataPath);
                    } else if (java.nio.file.Files.isDirectory(java.nio.file.Path.of("/usr/share/tesseract-ocr/4.00/tessdata"))) {
                        dataPath = "/usr/share/tesseract-ocr/4.00/tessdata";
                        t.setDatapath(dataPath);
                    } else {
                        log.warn("Tesseract tessdata missing at {}", dataPath);
                        tessReady = false;
                        return;
                    }
                }
                tess = t;
                tessReady = true;
                log.info("Local Tesseract OCR ready datapath={}", dataPath);
            } catch (Throwable ex) {
                tessReady = false;
                log.warn("Local Tesseract init failed: {}", ex.toString());
            }
        }
    }
}
