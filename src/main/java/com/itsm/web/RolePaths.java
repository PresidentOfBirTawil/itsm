package com.itsm.web;

import com.itsm.domain.Role;

/** Where each role's pages live. The three roles see the same incidents under different URL prefixes. */
public final class RolePaths {

    private RolePaths() {
    }

    /** The base path of the incident pages for a role, e.g. "/employee/incidents". */
    public static String incidents(Role role) {
        return switch (role) {
            case EMPLOYEE -> "/employee/incidents";
            case TECHNICIAN -> "/technician/incidents";
            case ADMIN -> "/admin/incidents";
        };
    }
}
