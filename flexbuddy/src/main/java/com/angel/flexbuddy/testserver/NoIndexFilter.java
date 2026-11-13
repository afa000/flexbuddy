package com.angel.flexbuddy.testserver;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/** Asks search engines not to index anything the test server sends, set before any later code can commit a response. */
final class NoIndexFilter implements Filter {

    static final String HEADER = "X-Robots-Tag";
    static final String VALUE = "noindex";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse http && !http.isCommitted()) {
            http.setHeader(HEADER, VALUE);
        }
        chain.doFilter(request, response);
    }
}
