package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.RedeemTransaction;
import com.petrolpump.discount.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RedeemTransactionRepository extends JpaRepository<RedeemTransaction, Long> {
    List<RedeemTransaction> findByBusinessDayOrderByCreatedAtDesc(String businessDay);
    List<RedeemTransaction> findByBusinessDayBetweenOrderByCreatedAtDesc(String from, String to);
    List<RedeemTransaction> findTop50ByOrderByCreatedAtDesc();
    List<RedeemTransaction> findByUserOrderByCreatedAtDesc(AppUser user);
}
