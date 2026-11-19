package com.mrdiet.security;

/**
 * The logged-in user, taken from the verified JWT (never from a user_id the
 * browser sends). Inject it in a controller with {@code @AuthenticationPrincipal AuthUser me}.
 */
public record AuthUser(long id, String email, String name, boolean admin) {}
