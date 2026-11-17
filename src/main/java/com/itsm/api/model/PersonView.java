package com.itsm.api.model;

import com.itsm.domain.Role;
import com.itsm.domain.User;

/** A person, as the API shows them. */
public record PersonView(Long id, String name, String email, Role role) {

    public static PersonView of(User user) {
        return user == null ? null : new PersonView(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
