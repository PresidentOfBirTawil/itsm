package com.itsm.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new technician also needs an email address and a first password to sign in with. */
public class NewTechnicianForm extends TechnicianForm {

    @NotBlank(message = "Please enter an email address")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email can be at most 150 characters")
    private String email;

    @NotNull(message = "Password must be 8 to 72 characters long")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters long")
    private String password;

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
}
