package com.itsm.api;

import java.io.IOException;
import java.util.Optional;

import com.itsm.domain.ApiToken;
import com.itsm.security.ItsmUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs in an API request by its key ({@code Authorization: Bearer qk_...}).
 * The request then runs as the key's owner, with exactly their permissions.
 * A wrong, revoked or expired key is answered with 401 at once; a key over
 * its limit with 429. Every answer says how many requests are left.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final ApiTokenService tokens;
    private final ApiRateLimiter rateLimiter;

    public ApiTokenFilter(ApiTokenService tokens, ApiRateLimiter rateLimiter) {
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);       // no key: public parts work, the rest answers 401
            return;
        }
        Optional<ApiToken> token = tokens.authenticate(header.substring(BEARER.length()).strip());
        if (token.isEmpty()) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"Quell API\", error=\"invalid_token\"");
            ProblemResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key",
                    "This API key does not exist, has been revoked, or has expired.", request.getRequestURI());
            return;
        }
        ApiRateLimiter.Decision decision = rateLimiter.take(token.get().getId());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.resetSeconds()));
            ProblemResponses.write(response, 429, "Too many requests",
                    "This key may make %d requests a minute. Try again in %d seconds."
                            .formatted(decision.limit(), decision.resetSeconds()), request.getRequestURI());
            return;
        }
        ItsmUserDetails owner = new ItsmUserDetails(token.get().getOwner());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner, null, owner.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
