package com.angel.flexbuddy.security;

import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/** The disabled namespace must return 404 for POST too, before the web chain's CSRF handling. */
public final class DisabledApiFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/v1") || path.startsWith("/api/v1/")) {
            response.setStatus(404);
            return;
        }
        chain.doFilter(request, response);
    }
}
