package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.AppUser;
import com.petrolpump.discount.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByPhone(String phone);
    boolean existsByPhone(String phone);
    List<AppUser> findByRoleInOrderByPhoneAsc(Collection<UserRole> roles);
}
