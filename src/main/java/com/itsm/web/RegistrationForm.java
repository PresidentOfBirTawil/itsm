package com.itsm.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a visitor types into the registration form, with the rules each field must meet. */
public class RegistrationForm {

    @NotBlank(message = "Please enter your name")
    @Size(max = 100, message = "Name can be at most 100 characters")
    private String name;

    @NotBlank(message = "Please enter your email address")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email can be at most 150 characters")
    private String email;

    // BCrypt only looks at the first 72 bytes of a password, so longer ones are not accepted.
    // An empty password is too short, so this one rule also covers "nothing typed".
    @NotNull(message = "Password must be 8 to 72 characters long")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters long")
    private String password;

    @NotBlank(message = "Please type the password again")
    private String confirmPassword;

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

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

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
