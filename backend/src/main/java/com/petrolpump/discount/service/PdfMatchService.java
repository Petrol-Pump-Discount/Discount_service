package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Match queued bill claims against SiteOmat Transaction Report PDFs.
 * Only the Transaction ID column is parsed; claims match on FCC ID / Trans ID.
 */
@Service
public class PdfMatchService {
    private static final Logger log = LoggerFactory.getLogger(PdfMatchService.class);

    /** SiteOmat: Receipt No ~x=40; Transaction ID sits at ~319 (empty preset) or ~379 (Money/Volume). */
    private static final float TXN_COLUMN_MIN_X = 280f;

    private static final Pattern NINE_DIGIT = Pattern.compile("^\\d{9}$");
    private static final Pattern LABELED_TXN = Pattern.compile(
            "(?i)(?:transaction\\s*id|trns\\.?\\s*id|txn\\s*id)\\s*[:#\\-]?\\s*(\\d{6,})");

    private final BillClaimRepository claims;
    private final RejectIdRepository rejectIds;
    private final LoyaltyConfigRepository configs;
    private final AppUserRepository users;
    private final DailyReportUploadRepository reports;

    public PdfMatchService(BillClaimRepository claims, RejectIdRepository rejectIds,
                           LoyaltyConfigRepository configs, AppUserRepository users,
                           DailyReportUploadRepository reports) {
        this.claims = claims; this.rejectIds = rejectIds; this.configs = configs;
        this.users = users; this.reports = reports;
    }

    @Transactional
    public Map<String, Object> processPdf(MultipartFile pdf, List<String> extraRejectIds) throws Exception {
        Set<String> txnIds = extractTransactionIdsFromPdf(pdf.getBytes());
        log.info("PDF match start file={} bytes={} transactionIdsParsed={} sample={}",
                pdf.getOriginalFilename(),
                pdf.getSize(),
                txnIds.size(),
                txnIds.stream().limit(40).collect(Collectors.joining(",")));

        if (extraRejectIds != null) {
            for (String r : extraRejectIds) {
                String key = last9(r);
                if (key != null && !rejectIds.existsByReceiptKey(key)) {
                    RejectId row = new RejectId();
                    row.setReceiptKey(key);
                    rejectIds.save(row);
                    log.info("PDF match force-reject id added key={}", key);
                }
            }
        }

        LoyaltyConfig cfg = configs.findById(1L).orElseGet(() -> configs.save(new LoyaltyConfig()));
        int matched = 0, rejected = 0;
        List<BillClaim> queued = claims.findByStatusOrderByCreatedAtAsc(ClaimStatus.QUEUED);
        log.info("PDF match queuedClaims={}", queued.size());

        for (BillClaim c : queued) {
            List<String> billKeys = billMatchKeys(c);
            boolean inRejectList = billKeys.stream().anyMatch(rejectIds::existsByReceiptKey);
            String matchedKey = billKeys.stream().filter(txnIds::contains).findFirst().orElse(null);
            boolean inPdf = matchedKey != null;

            if (inRejectList || !inPdf) {
                String reason = inRejectList ? "In reject list" : "Not found in PDF Transaction ID";
                c.setStatus(ClaimStatus.REJECTED);
                c.setRejectReason(reason);
                c.setDecidedAt(Instant.now());
                rejected++;
                log.info("PDF match REJECT claimId={} phone={} billNo={} receiptKey={} fccId={} transId={} billKeys={} reason={} inPdf={}",
                        c.getId(), c.getUser().getPhone(), c.getBillNo(), c.getReceiptKey(),
                        c.getFccId(), c.getTransId(), billKeys, reason, inPdf);
                continue;
            }

            Instant since = BusinessDay.startOfTrailingDays(30);
            double prior = claims.sumApprovedVolumeSince(c.getUser(), since);
            long base = CoinCalculator.baseCoins(c.getVolumeLitres(), cfg);
            long credit = CoinCalculator.withBonus(base, prior, cfg);
            c.setStatus(ClaimStatus.APPROVED);
            c.setCoinsCredited(credit);
            c.setDecidedAt(Instant.now());
            AppUser u = c.getUser();
            u.setWalletCoins(u.getWalletCoins() + credit);
            users.save(u);
            matched++;
            log.info("PDF match APPROVE claimId={} phone={} matchedTxnId={} volume={} coins={}",
                    c.getId(), c.getUser().getPhone(), matchedKey, c.getVolumeLitres(), credit);
        }

        DailyReportUpload rep = new DailyReportUpload();
        rep.setFileName(pdf.getOriginalFilename());
        rep.setReceiptKeysCsv(String.join(",", txnIds));
        rep.setMatchedCount(matched);
        rep.setRejectedCount(rejected);
        reports.save(rep);

        log.info("PDF match done approved={} rejected={} transactionIdsParsed={}", matched, rejected, txnIds.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactionIdsParsed", txnIds.size());
        out.put("receiptsParsed", txnIds.size());
        out.put("approved", matched);
        out.put("rejected", rejected);
        return out;
    }

    /**
     * Read only the Transaction ID column from a SiteOmat PDF (by glyph X position).
     * Falls back to labeled text parsing if no column IDs are found.
     */
    static Set<String> extractTransactionIdsFromPdf(byte[] bytes) throws IOException {
        Set<String> fromColumn = extractTxnIdsByColumnPosition(bytes);
        if (!fromColumn.isEmpty()) return fromColumn;
        String text = extractPdfText(bytes);
        return extractTransactionIds(text);
    }

    static Set<String> extractTxnIdsByColumnPosition(byte[] bytes) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            List<String> ids = new ArrayList<>();
            List<Float> xs = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (positions == null || positions.isEmpty()) return;
                    String t = text == null ? "" : text.trim();
                    if (!NINE_DIGIT.matcher(t).matches()) return;
                    ids.add(t);
                    xs.add(positions.get(0).getXDirAdj());
                }
            };
            stripper.getText(doc);
            for (int i = 0; i < ids.size(); i++) {
                if (xs.get(i) >= TXN_COLUMN_MIN_X) {
                    String key = last9(ids.get(i));
                    if (key != null) out.add(key);
                }
            }
        }
        return out;
    }

    static String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            return stripper.getText(doc);
        }
    }

    /**
     * Text fallback for simple tabular / labeled PDFs (unit tests + non-SiteOmat exports).
     * Prefer {@link #extractTransactionIdsFromPdf(byte[])} for real SiteOmat reports.
     */
    static Set<String> extractTransactionIds(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return out;

        Matcher labeled = LABELED_TXN.matcher(text);
        while (labeled.find()) {
            String key = last9(labeled.group(1));
            if (key != null) out.add(key);
        }
        out.addAll(extractByTransactionIdColumn(text));
        return out;
    }

    static Set<String> extractByTransactionIdColumn(String text) {
        Set<String> out = new LinkedHashSet<>();
        String[] lines = text.split("\\R");
        int txnCol = -1;
        int headerIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String[] cols = splitRow(lines[i]);
            for (int c = 0; c < cols.length; c++) {
                if (cols[c].equalsIgnoreCase("TransactionId")
                        || cols[c].equalsIgnoreCase("TxnId")
                        || cols[c].equalsIgnoreCase("TrnsId")) {
                    txnCol = c;
                    headerIdx = i;
                    break;
                }
            }
            if (txnCol >= 0) break;
        }
        if (txnCol < 0) return out;
        for (int i = headerIdx + 1; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) continue;
            String[] cols = splitRow(raw);
            if (looksLikeHeaderRow(cols)) break;
            if (cols.length <= txnCol) continue;
            String key = last9(cols[txnCol]);
            if (key != null) out.add(key);
        }
        return out;
    }

    /** FCC ID + Trans ID from the bill (OCR); either may equal PDF Transaction ID. */
    static List<String> billMatchKeys(BillClaim c) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (c.getReceiptKey() != null && !c.getReceiptKey().isBlank()) keys.add(c.getReceiptKey());
        String fcc = last9(c.getFccId());
        String trans = last9(c.getTransId());
        if (fcc != null) keys.add(fcc);
        if (trans != null) keys.add(trans);
        return new ArrayList<>(keys);
    }

    private static boolean looksLikeHeaderRow(String[] cols) {
        for (String c : cols) {
            if (c.equalsIgnoreCase("TransactionId")
                    || c.equalsIgnoreCase("ReceiptNo")
                    || c.equalsIgnoreCase("FccId")) {
                return true;
            }
        }
        return false;
    }

    static String[] splitRow(String line) {
        String n = line
                .replaceAll("(?i)transaction\\s*id", "TransactionId")
                .replaceAll("(?i)trns\\.?\\s*id", "TrnsId")
                .replaceAll("(?i)txn\\s*id", "TxnId")
                .replaceAll("(?i)receipt\\s*no\\.?", "ReceiptNo")
                .replaceAll("(?i)fcc\\s*id", "FccId");
        return n.trim().split("\\s+");
    }

    static String last9(String raw) {
        return BillOcrResult.normalizeReceiptDigits(raw);
    }
}
