package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.RedeemTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RedeemTransactionRepository extends JpaRepository<RedeemTransaction, Long> {
    List<RedeemTransaction> findByBusinessDayOrderByCreatedAtDesc(String businessDay);
    List<RedeemTransaction> findTop50ByOrderByCreatedAtDesc();
}
