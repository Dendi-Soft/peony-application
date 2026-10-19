package com.dendi.peony.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Signin request.
 *
 * @param username account username or email address
 * @param password plain-text password
 */
public record SigninRequest(@NotBlank String username, @NotBlank String password) {}
