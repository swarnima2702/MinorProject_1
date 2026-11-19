package com.mrdiet.security;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads "Authorization: Bearer <jwt>", checks the signature, and loads the user
 * fresh from the database on every request — so removing someone's admin flag
 * takes effect immediately.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final Db db;

    public JwtAuthFilter(JwtService jwt, Db db) {
        this.jwt = jwt;
        this.db = db;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = jwt.userIdFrom(header.substring(7).trim());
            if (userId != null) {
                Map<String, Object> row = db.one("SELECT id, email, name, is_admin FROM users WHERE id = ?", userId);
                if (row != null) {
                    boolean admin = Rows.bool(row, "is_admin");
                    AuthUser user = new AuthUser(userId, Rows.str(row, "email"), Rows.str(row, "name"), admin);
                    List<SimpleGrantedAuthority> roles = new ArrayList<>();
                    roles.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (admin) roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    SecurityContextHolder.getContext()
                            .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, roles));
                }
            }
        }
        chain.doFilter(request, response);
    }
}
