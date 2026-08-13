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

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestParam(required = false) String phone,
            @RequestParam String vehicleNo,
            @RequestParam MultipartFile image,
            @RequestParam double lat,
            @RequestParam double lng
    ) throws Exception {
        var c = claims.upload(sessionToken, phone, vehicleNo, image, lat, lng);
        return Map.of(
                "id", c.getId(),
                "status", c.getStatus().name(),
                "receiptKey", c.getReceiptKey(),
                "volumeLitres", c.getVolumeLitres(),
                "message", "Submitted for verification. Coins after daily confirmation."
        );
    }
}
