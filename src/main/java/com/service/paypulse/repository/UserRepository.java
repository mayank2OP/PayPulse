package com.service.paypulse.repository;

import java.util.Optional;
import java.util.UUID;

import com.service.paypulse.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(@NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email);
}
