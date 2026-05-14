package com.rabbithole.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
@Slf4j
public class ApiLoggingConfig {

    @Value("${app.logging.slow-request-ms:1200}")
    private long slowRequestMs;

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> apiLoggingFilter() {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        reg.addUrlPatterns("/api/*");
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                    throws ServletException, IOException {
                String requestId = req.getHeader("X-Request-Id");
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString().substring(0, 12);
                }
                resp.setHeader("X-Request-Id", requestId);
                long start = System.currentTimeMillis();
                try {
                    chain.doFilter(req, resp);
                } finally {
                    long cost = System.currentTimeMillis() - start;
                    if (resp.getStatus() >= 500 || cost >= slowRequestMs) {
                        log.warn("api_request id={} method={} path={} status={} costMs={} remote={}",
                                requestId,
                                req.getMethod(),
                                req.getRequestURI(),
                                resp.getStatus(),
                                cost,
                                req.getRemoteAddr());
                    } else {
                        log.info("api_request id={} method={} path={} status={} costMs={}",
                                requestId,
                                req.getMethod(),
                                req.getRequestURI(),
                                resp.getStatus(),
                                cost);
                    }
                }
            }
        });
        return reg;
    }
}
