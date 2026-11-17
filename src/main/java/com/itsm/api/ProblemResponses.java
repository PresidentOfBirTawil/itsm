package com.itsm.api;

import java.io.IOException;

import com.itsm.config.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Errors of the API in the standard "problem details" format (RFC 9457),
 * for the cases that happen before a controller is reached: no key, a bad
 * key, too many requests, no permission.
 */
public final class ProblemResponses {

    public static final String PROBLEM_JSON = "application/problem+json";

    private ProblemResponses() {
    }

    public static void write(HttpServletResponse response, int status, String title, String detail, String instance)
            throws IOException {
        response.setStatus(status);
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":%s,\"status\":%d,\"detail\":%s,\"instance\":%s}"
                .formatted(Json.quote(title), status, Json.quote(detail), Json.quote(instance)));
    }

    /** No key, or the key was not accepted. */
    public static void unauthorized(HttpServletRequest request, HttpServletResponse response,
                                    AuthenticationException e) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"Quell API\"");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Send an API key in the Authorization header: \"Authorization: Bearer qk_...\". Keys are made under Account, API keys.",
                request.getRequestURI());
    }

    /** A valid key, but its owner may not do this. */
    public static void forbidden(HttpServletRequest request, HttpServletResponse response,
                                 AccessDeniedException e) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "The owner of this API key is not allowed to do this.", request.getRequestURI());
    }
}
