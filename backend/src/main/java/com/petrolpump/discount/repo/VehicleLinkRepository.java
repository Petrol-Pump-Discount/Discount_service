package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.AppUser;
import com.petrolpump.discount.domain.VehicleLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface VehicleLinkRepository extends JpaRepository<VehicleLink, Long> {
    List<VehicleLink> findByUser(AppUser user);
    Optional<VehicleLink> findByUserAndRegNo(AppUser user, String regNo);
    boolean existsByUserAndRegNo(AppUser user, String regNo);
}
