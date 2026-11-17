package com.itsm.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Checks email and password like the normal provider, but first asks
 * {@link LoginAttempts} whether sign-in for the address is paused, and
 * tells it about every wrong password. While paused, even the right password
 * is refused, so guessing on is pointless.
 */
public class LockoutAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider passwords;
    private final LoginAttempts attempts;

    public LockoutAuthenticationProvider(AuthenticationProvider passwords, LoginAttempts attempts) {
        this.passwords = passwords;
        this.attempts = attempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        if (attempts.isLocked(email)) {
            throw new LockedException("Sign-in for this account is paused after too many wrong passwords.");
        }
        try {
            Authentication result = passwords.authenticate(authentication);
            attempts.succeeded(email);
            return result;
        } catch (BadCredentialsException e) {
            if (attempts.failed(email)) {
                throw new LockedException("Sign-in for this account is paused after too many wrong passwords.", e);
            }
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return passwords.supports(authentication);
    }
}
