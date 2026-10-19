package com.dendi.peony.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signup request for a new account.
 *
 * @param username desired unique username
 * @param email desired unique email address
 * @param password plain-text password (hashed server-side, never stored plain)
 * @param firstName account holder first name
 * @param lastName account holder last name
 */
public record SignupRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank String firstName,
    @NotBlank String lastName) {}
