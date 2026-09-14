package io.github.johneliud.identity_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.johneliud.identity_service.model.ResetToken;

@Repository
public interface ResetTokenRepository extends JpaRepository<ResetToken, UUID> {
    Optional<ResetToken> findByTokenHashAndUsedFalse(String tokenHash);
}
