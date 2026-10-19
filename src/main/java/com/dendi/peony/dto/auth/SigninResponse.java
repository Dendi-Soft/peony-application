package com.dendi.peony.dto.auth;

/**
 * Signin response: the issued session id plus the authenticated user's profile.
 *
 * <p>The session is server-side; {@code sessionId} is the JSESSIONID the client may also carry
 * explicitly, while the {@code Set-Cookie} header is the primary transport.
 *
 * @param sessionId id of the server-side session created for this sign-in
 * @param profile the authenticated user's profile with tier and capabilities
 */
public record SigninResponse(String sessionId, ProfileResponse profile) {}
