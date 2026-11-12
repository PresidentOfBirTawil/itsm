package com.itsm.web;

import com.itsm.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an administrator fills in to add an employee or another administrator. */
public class UserForm {

    @NotBlank(message = "Please enter a name")
    @Size(max = 100, message = "The name can be at most 100 characters")
    private String name;

    @NotBlank(message = "Please enter an email address")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email can be at most 150 characters")
    private String email;

    @NotNull(message = "Password must be 8 to 72 characters long")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters long")
    private String password;

    @NotNull(message = "Please choose a role")
    private Role role;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
