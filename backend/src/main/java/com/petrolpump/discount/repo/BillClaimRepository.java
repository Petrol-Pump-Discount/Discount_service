package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface BillClaimRepository extends JpaRepository<BillClaim, Long> {
    Optional<BillClaim> findByReceiptKey(String receiptKey);
    boolean existsByReceiptKey(String receiptKey);
    boolean existsByBillNoIgnoreCase(String billNo);
    List<BillClaim> findByStatusOrderByCreatedAtAsc(ClaimStatus status);
    List<BillClaim> findByStatusOrderByCreatedAtDesc(ClaimStatus status);
    List<BillClaim> findByUserAndCreatedAtAfter(AppUser user, Instant after);
    List<BillClaim> findByUserOrderByCreatedAtDesc(AppUser user);
    @Query("select coalesce(sum(c.volumeLitres),0) from BillClaim c where c.user = ?1 and c.status = com.petrolpump.discount.domain.ClaimStatus.APPROVED and c.decidedAt >= ?2")
    double sumApprovedVolumeSince(AppUser user, Instant since);
    long countByUserAndCreatedAtAfter(AppUser user, Instant after);
}
