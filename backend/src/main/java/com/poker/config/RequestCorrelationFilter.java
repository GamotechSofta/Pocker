package com.poker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var id = request.getHeader("X-Request-Id");
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,80}")) id = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", id);
        try (var scope = MDC.putCloseable("requestId", id)) {
            chain.doFilter(request, response);
        }
    }
}
