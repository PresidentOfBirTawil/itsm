package com.itsm.security;

import java.util.Collection;
import java.util.List;

import com.itsm.domain.Role;
import com.itsm.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user as Spring Security sees it. It is a small snapshot of
 * our {@link User} taken at sign-in (id, name, email, password hash, role),
 * kept in the HTTP session.
 */
public class ItsmUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;

    public ItsmUserDetails(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    /** One authority per user: ROLE_EMPLOYEE, ROLE_TECHNICIAN or ROLE_ADMIN. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** People sign in with their email address. */
    @Override
    public String getUsername() {
        return email;
    }
}
