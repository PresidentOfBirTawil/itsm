package com.quell.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * The rules for the REST API (/api/**), separate from the web pages:
 * signed in by API key on every request, no session and no cookies (so no
 * CSRF tokens are needed: a browser cannot send the key by itself), and
 * errors as JSON instead of pages. The documentation is public.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiTokenService tokens,
                                                      ApiRateLimiter rateLimiter) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/openapi.json", "/api/openapi.json/**", "/api/docs", "/api/docs/**",
                                "/api/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(ProblemResponses::unauthorized)
                        .accessDeniedHandler(ProblemResponses::forbidden))
                .addFilterBefore(new ApiTokenFilter(tokens, rateLimiter), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
