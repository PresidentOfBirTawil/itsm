package com.itsm.security;

import com.itsm.domain.User;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/** Ends the sessions of a user, for example when an administrator disables the account. */
@Component
public class SignedInSessions {

    private final SessionRegistry sessionRegistry;

    public SignedInSessions(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Signs the user out everywhere. Their next request, from any browser,
     * is sent to the sign-in page.
     *
     * @return how many sessions were ended
     */
    public int signOut(User user) {
        var sessions = sessionRegistry.getAllSessions(new ItsmUserDetails(user), false);
        sessions.forEach(SessionInformation::expireNow);
        return sessions.size();
    }
}
