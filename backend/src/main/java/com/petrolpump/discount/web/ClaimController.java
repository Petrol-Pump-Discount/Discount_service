package com.petrolpump.discount.web;

import com.petrolpump.discount.service.ClaimService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimService claims;
    public ClaimController(ClaimService claims) { this.claims = claims; }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam String phone,
            @RequestParam String vehicleNo,
            @RequestParam MultipartFile image,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam String receiptKey,
            @RequestParam(required = false) String billNo,
            @RequestParam double volume,
            @RequestParam(required = false, defaultValue = "false") boolean duplicateFlag
    ) throws Exception {
        var c = claims.guestUpload(phone, vehicleNo, image, lat, lng, receiptKey, billNo, volume, duplicateFlag);
        return Map.of(
                "id", c.getId(),
                "status", c.getStatus().name(),
                "receiptKey", c.getReceiptKey(),
                "message", "Submitted for verification. Coins after daily confirmation."
        );
    }
}
