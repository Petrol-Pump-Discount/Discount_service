package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.Pump;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PumpRepository extends JpaRepository<Pump, Long> {
    Optional<Pump> findByRedeemToken(String redeemToken);
}
