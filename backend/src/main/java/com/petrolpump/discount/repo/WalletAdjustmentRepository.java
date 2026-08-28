package com.petrolpump.discount.repo;

import com.petrolpump.discount.domain.AppUser;
import com.petrolpump.discount.domain.WalletAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletAdjustmentRepository extends JpaRepository<WalletAdjustment, Long> {
    List<WalletAdjustment> findByUserOrderByCreatedAtDesc(AppUser user);
    List<WalletAdjustment> findTop100ByOrderByCreatedAtDesc();
}
