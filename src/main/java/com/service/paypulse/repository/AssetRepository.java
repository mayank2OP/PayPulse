package com.service.paypulse.repository;

import java.util.Optional;
import java.util.UUID;

import com.service.paypulse.model.Asset;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByCodeIgnoreCase(@NotBlank(message = "Asset code is required") String code);
}
