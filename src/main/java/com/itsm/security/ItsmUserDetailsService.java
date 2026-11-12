package com.itsm.security;

import com.itsm.domain.User;
import com.itsm.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks a user up by email when someone signs in. Spring Security then checks the password. */
@Service
public class ItsmUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public ItsmUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(User.normaliseEmail(email))
                .map(ItsmUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
