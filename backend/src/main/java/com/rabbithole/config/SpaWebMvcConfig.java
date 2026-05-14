package com.rabbithole.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File avatarDir = Paths.get(System.getProperty("user.home"), ".rabbithole", "avatars").toFile();
        // Use file: URL with trailing slash so Spring resolves it as a directory
        String location = "file:" + avatarDir.getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations(location);
    }

    /**
     * SPA 路由回退：非 API、非静态资源的请求全部转发到 /index.html，
     * 让前端 Vue Router 处理路由。
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> spaFilter() {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
        reg.addUrlPatterns("/*");
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse resp,
                                            FilterChain chain) throws ServletException, IOException {
                String path = req.getRequestURI();
                // /api 开头、或带文件后缀的请求不走 SPA 回退
                if (path.startsWith("/api") || path.contains(".") || path.equals("/")) {
                    chain.doFilter(req, resp);
                    return;
                }
                // 转发到 index.html，让前端路由接管
                req.getRequestDispatcher("/index.html").forward(req, resp);
            }
        });
        return reg;
    }
}
