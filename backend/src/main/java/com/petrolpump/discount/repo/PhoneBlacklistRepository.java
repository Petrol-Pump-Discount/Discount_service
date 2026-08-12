package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.PhoneBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PhoneBlacklistRepository extends JpaRepository<PhoneBlacklist, Long> {
    boolean existsByPhone(String phone);
}
