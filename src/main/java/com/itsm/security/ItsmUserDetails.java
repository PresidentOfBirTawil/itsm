package com.itsm.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    private final boolean enabled;

    public ItsmUserDetails(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
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

    /** Spring Security refuses the sign-in of an account that an administrator has disabled. */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** People sign in with their email address. */
    @Override
    public String getUsername() {
        return email;
    }

    /** Two snapshots of the same user are the same principal, which is how a user's sessions are found. */
    @Override
    public boolean equals(Object other) {
        return other instanceof ItsmUserDetails that && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
