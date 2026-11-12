package com.itsm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may open which URL. The rules are checked on the server for every
 * request, so hiding a link in the navbar is never the only protection.
 *
 * <ul>
 *   <li>/login, /register and static files: everyone</li>
 *   <li>/employee/**: role EMPLOYEE</li>
 *   <li>/technician/**: role TECHNICIAN</li>
 *   <li>/admin/** (and the H2 console in dev): role ADMIN</li>
 *   <li>anything else: any signed-in user</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String H2_CONSOLE = "/h2-console/**";

    /** True only when the H2 console is switched on (the dev profile). */
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/login", "/register", "/error").permitAll();
            auth.requestMatchers("/css/**", "/webjars/**", "/favicon.svg").permitAll();
            auth.requestMatchers("/employee/**").hasRole("EMPLOYEE");
            auth.requestMatchers("/technician/**").hasRole("TECHNICIAN");
            auth.requestMatchers("/admin/**").hasRole("ADMIN");
            if (h2ConsoleEnabled) {
                auth.requestMatchers(H2_CONSOLE).hasRole("ADMIN");
            }
            auth.anyRequest().authenticated();
        });

        http.formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error"));

        http.logout(logout -> logout
                .logoutSuccessUrl("/login?logout"));

        if (h2ConsoleEnabled) {
            // The H2 console is a small third-party web app: it has no CSRF
            // tokens and shows its pages in frames, so relax both for it only.
            http.csrf(csrf -> csrf.ignoringRequestMatchers(H2_CONSOLE));
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }

    /** Passwords are stored as BCrypt hashes, never as plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
