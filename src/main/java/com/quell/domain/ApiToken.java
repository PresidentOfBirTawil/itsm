package com.quell.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A key that lets another program use the REST API on behalf of a user,
 * with exactly that user's permissions. Only a SHA-256 hash of the key is
 * stored: the key itself is shown once, when it is made, and cannot be
 * recovered from the database.
 */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** SHA-256 of the key, in hex. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** The start of the key, to recognise it in a list, e.g. "qk_7Fh2". */
    @Column(nullable = false, length = 12)
    private String prefix;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    private LocalDateTime revokedAt;

    protected ApiToken() {
        // for JPA
    }

    public ApiToken(User owner, String name, String tokenHash, String prefix, LocalDateTime createdAt,
                    LocalDateTime expiresAt) {
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.prefix = Objects.requireNonNull(prefix);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = expiresAt;
    }

    /** Whether the key may be used at this moment. */
    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || now.isBefore(expiresAt)) && owner.isEnabled();
    }

    public void revoke(LocalDateTime when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public void markUsed(LocalDateTime when) {
        lastUsedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public User getOwner() {
        return owner;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPrefix() {
        return prefix;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
