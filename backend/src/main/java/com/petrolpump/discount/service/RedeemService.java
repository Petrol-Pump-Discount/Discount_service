package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class RedeemService {
    private final PumpRepository pumps;
    private final AppUserRepository users;
    private final RedeemTransactionRepository redeems;

    public RedeemService(PumpRepository pumps, AppUserRepository users, RedeemTransactionRepository redeems) {
        this.pumps = pumps; this.users = users; this.redeems = redeems;
    }

    public Pump requirePumpByToken(String token) {
        return pumps.findByRedeemToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid pump QR"));
    }

    @Transactional
    public RedeemTransaction redeem(AppUser user, String pumpToken, Long coins, Double rupees) {
        Pump pump = requirePumpByToken(pumpToken);
        long amount;
        if (coins != null && coins > 0) amount = coins;
        else if (rupees != null && rupees > 0) amount = Math.round(rupees * 100);
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter coins or rupees");
        if (amount < 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum ₹1 (100 coins)");
        if (user.getWalletCoins() < amount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }
        user.setWalletCoins(user.getWalletCoins() - amount);
        users.save(user);
        RedeemTransaction tx = new RedeemTransaction();
        tx.setUser(user);
        tx.setPump(pump);
        tx.setCoins(amount);
        tx.setRupeesPaise(amount);
        tx.setBusinessDay(BusinessDay.of(Instant.now()));
        return redeems.save(tx);
    }
}
