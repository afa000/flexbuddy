package com.angel.flexbuddy.api;

import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

final class ApiTokenFilter extends OncePerRequestFilter {
    static final String TOKEN_ID = ApiTokenFilter.class.getName() + ".tokenId";
    private final AppTokenService tokens;
    private final UserDetailsService users;
    ApiTokenFilter(AppTokenService tokens, UserDetailsService users) {
        this.tokens = tokens;
        this.users = users;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The API accepts only the header, never an inherited browser authentication.
        SecurityContextHolder.clearContext();
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.authenticate(header.substring(7)).ifPresent(identity -> {
                try {
                    UserDetails user = users.loadUserByUsername(identity.email());
                    var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute(TOKEN_ID, identity.tokenId());
                } catch (UsernameNotFoundException ignored) {
                    // Account was removed after the token lookup: remain unauthenticated.
                }
            });
        }
        chain.doFilter(request, response);
    }
}
