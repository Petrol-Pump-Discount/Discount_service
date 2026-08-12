package com.petrolpump.discount.repo;
import com.petrolpump.discount.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserSessionRepository extends JpaRepository<UserSession, String> {}
