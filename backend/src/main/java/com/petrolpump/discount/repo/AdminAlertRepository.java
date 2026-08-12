package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.AdminAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AdminAlertRepository extends JpaRepository<AdminAlert, Long> {
    List<AdminAlert> findTop50ByOrderByCreatedAtDesc();
}
