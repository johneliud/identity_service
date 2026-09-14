package io.github.johneliud.identity_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.johneliud.identity_service.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
            SELECT DISTINCT u FROM User u LEFT JOIN u.roles r
            WHERE (:status IS NULL OR CAST(u.status AS string) = :status)
            AND (:email IS NULL OR u.email LIKE %:email%)
            AND (:roleName IS NULL OR r.name = :roleName)
            """)
    Page<User> findAllFiltered(
            @Param("status") String status,
            @Param("email") String email,
            @Param("roleName") String roleName,
            Pageable pageable);
}
