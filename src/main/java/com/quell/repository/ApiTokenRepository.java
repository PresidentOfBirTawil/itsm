package com.quell.repository;

import java.util.List;
import java.util.Optional;

import com.quell.domain.ApiToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    long countByOwnerIdAndRevokedAtIsNull(Long ownerId);
}
