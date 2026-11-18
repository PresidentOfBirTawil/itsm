package com.quell.api.model;

import com.quell.domain.Role;
import com.quell.domain.User;

/** A person, as the API shows them. */
public record PersonView(Long id, String name, String email, Role role) {

    public static PersonView of(User user) {
        return user == null ? null : new PersonView(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
