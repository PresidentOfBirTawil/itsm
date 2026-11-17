package com.itsm.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.ApiToken;
import com.itsm.domain.Role;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.repository.ApiTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Making, checking and revoking API keys.
 *
 * <p>A key looks like {@code qk_} followed by 40 random letters and digits
 * (about 238 bits of randomness). Only its SHA-256 hash is stored; a plain
 * hash is enough because the key is random and long, unlike a password.</p>
 */
@Service
public class ApiTokenService {

    public static final String PREFIX = "qk_";
    static final int RANDOM_CHARACTERS = 40;
    /** How many working keys one user may have. */
    public static final int MAX_PER_USER = 10;
    /** "Last used" is saved at most this often, so that busy keys do not write on every request. */
    static final Duration LAST_USED_PRECISION = Duration.ofMinutes(1);

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiTokenRepository tokens;

    public ApiTokenService(ApiTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** A new key and the one and only time its full text is available. */
    public record CreatedToken(ApiToken token, String key) {
    }

    /**
     * Makes a key for a user.
     *
     * @param days how long it works, or null for no end
     */
    @Transactional
    public CreatedToken create(User owner, String name, Integer days) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new InvalidInputException("Give the key a name of 1 to 80 characters, e.g. the tool that will use it.");
        }
        if (days != null && (days < 1 || days > 3650)) {
            throw new InvalidInputException("A key can work for 1 to 3650 days, or without an end.");
        }
        if (tokens.countByOwnerIdAndRevokedAtIsNull(owner.getId()) >= MAX_PER_USER) {
            throw new InvalidInputException("You already have %d keys. Revoke one you no longer use first.".formatted(MAX_PER_USER));
        }
        StringBuilder key = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_CHARACTERS; i++) {
            key.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        LocalDateTime now = LocalDateTime.now();
        ApiToken token = tokens.save(new ApiToken(owner, cleanName, hash(key.toString()),
                key.substring(0, PREFIX.length() + 4), now, days == null ? null : now.plusDays(days)));
        return new CreatedToken(token, key.toString());
    }

    /**
     * The key behind a presented text, if it exists and may be used now.
     * Notes when it was used.
     */
    @Transactional
    public Optional<ApiToken> authenticate(String presented) {
        if (presented == null || !presented.startsWith(PREFIX) || presented.length() != PREFIX.length() + RANDOM_CHARACTERS) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiToken> found = tokens.findByTokenHash(hash(presented)).filter(token -> token.isUsable(now));
        found.ifPresent(token -> {
            if (token.getLastUsedAt() == null || token.getLastUsedAt().isBefore(now.minus(LAST_USED_PRECISION))) {
                token.markUsed(now);
            }
        });
        return found;
    }

    /** The keys of a user, newest first, revoked ones included. */
    @Transactional(readOnly = true)
    public List<ApiToken> of(User owner) {
        return tokens.findByOwnerIdOrderByCreatedAtDescIdDesc(owner.getId());
    }

    /** Stops a key from working, at once. Its owner can do this, and administrators. */
    @Transactional
    public ApiToken revoke(Long id, User actor) {
        ApiToken token = tokens.findById(id)
                .filter(found -> found.getOwner().equals(actor) || actor.getRole() == Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("There is no key %d.".formatted(id)));
        token.revoke(LocalDateTime.now());
        return token;
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
