package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.AppUser;
import com.petrolpump.discount.domain.WalletAdjustment;
import com.petrolpump.discount.repo.AppUserRepository;
import com.petrolpump.discount.repo.WalletAdjustmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletAdjustmentService {
    private final AppUserRepository users;
    private final WalletAdjustmentRepository adjustments;

    public WalletAdjustmentService(AppUserRepository users, WalletAdjustmentRepository adjustments) {
        this.users = users;
        this.adjustments = adjustments;
    }

    @Transactional
    public WalletAdjustment adjust(AppUser admin, String phone, long deltaCoins, String reason) {
        if (deltaCoins == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coin change cannot be zero");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty() || r.length() > 280) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a reason (max 280 chars)");
        }
        String p = AuthService.normalizePhone(phone);
        AppUser user = users.findByPhone(p)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account for that phone"));
        long next = user.getWalletCoins() + deltaCoins;
        if (next < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not enough coins (wallet has " + user.getWalletCoins() + ")");
        }
        user.setWalletCoins(next);
        users.save(user);

        WalletAdjustment row = new WalletAdjustment();
        row.setUser(user);
        row.setAdmin(admin);
        row.setDeltaCoins(deltaCoins);
        row.setBalanceAfter(next);
        row.setReason(r);
        return adjustments.save(row);
    }
}
