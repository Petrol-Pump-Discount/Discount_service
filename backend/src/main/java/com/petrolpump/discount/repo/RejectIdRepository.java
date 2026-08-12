package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.RejectId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RejectIdRepository extends JpaRepository<RejectId, Long> {
    boolean existsByReceiptKey(String receiptKey);
}
