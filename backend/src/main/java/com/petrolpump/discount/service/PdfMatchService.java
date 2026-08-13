package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PdfMatchService {
    private static final Logger log = LoggerFactory.getLogger(PdfMatchService.class);

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
        String text = new String(pdf.getBytes(), StandardCharsets.ISO_8859_1);
        String utf = new String(pdf.getBytes(), StandardCharsets.UTF_8);
        if (utf.length() > text.length()) text = utf;

        Set<String> receipts = extractReceiptNos(text);
        log.info("PDF match start file={} bytes={} receiptKeysParsed={} sample={}",
                pdf.getOriginalFilename(),
                pdf.getSize(),
                receipts.size(),
                receipts.stream().limit(30).collect(Collectors.joining(",")));

        if (extraRejectIds != null) {
            for (String r : extraRejectIds) {
                String key = last9(r);
                if (!rejectIds.existsByReceiptKey(key)) {
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
            String key = c.getReceiptKey();
            boolean inRejectList = rejectIds.existsByReceiptKey(key);
            boolean inPdf = receipts.contains(key);
            if (inRejectList || !inPdf) {
                String reason = inRejectList ? "In reject list" : "Not found in PDF Receipt No";
                c.setStatus(ClaimStatus.REJECTED);
                c.setRejectReason(reason);
                c.setDecidedAt(Instant.now());
                rejected++;
                log.info("PDF match REJECT claimId={} phone={} vehicle={} billNo={} receiptKey={} volume={} reason={} inPdf={} inRejectList={}",
                        c.getId(),
                        c.getUser().getPhone(),
                        c.getVehicleNo(),
                        c.getBillNo(),
                        key,
                        c.getVolumeLitres(),
                        reason,
                        inPdf,
                        inRejectList);
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
            log.info("PDF match APPROVE claimId={} phone={} receiptKey={} volume={} coins={} prior30dVol={}",
                    c.getId(), c.getUser().getPhone(), key, c.getVolumeLitres(), credit, prior);
        }

        DailyReportUpload rep = new DailyReportUpload();
        rep.setFileName(pdf.getOriginalFilename());
        rep.setReceiptKeysCsv(String.join(",", receipts));
        rep.setMatchedCount(matched);
        rep.setRejectedCount(rejected);
        reports.save(rep);

        log.info("PDF match done approved={} rejected={} receiptsParsed={}", matched, rejected, receipts.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptsParsed", receipts.size());
        out.put("approved", matched);
        out.put("rejected", rejected);
        return out;
    }

    static Set<String> extractReceiptNos(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?<!\\d)(\\d{9})(?!\\d)").matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static String last9(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.length() > 9) d = d.substring(d.length() - 9);
        if (d.length() < 9) d = String.format("%9s", d).replace(' ', '0');
        return d;
    }
}
