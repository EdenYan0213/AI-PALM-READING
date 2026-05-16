package com.palmistrylab.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Content-Type")
        .allowCredentials(false)
        .maxAge(3600);
  }

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
    FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new CorsFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<Utf8Filter> utf8FilterRegistration() {
    FilterRegistrationBean<Utf8Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new Utf8Filter());
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  static class CorsFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
      String origin = request.getHeader("Origin");
      if (origin == null || origin.isEmpty()) {
        origin = "*";
      }
      response.setHeader("Access-Control-Allow-Origin", origin);
      response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
      response.setHeader("Access-Control-Allow-Headers", "*");
      response.setHeader("Access-Control-Expose-Headers", "Content-Type");
      response.setHeader("Access-Control-Max-Age", "3600");
      if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        response.setStatus(HttpServletResponse.SC_OK);
        return;
      }
      filterChain.doFilter(request, response);
    }
  }

  static class Utf8Filter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
      response.setCharacterEncoding("UTF-8");
      filterChain.doFilter(request, response);
      String ct = response.getContentType();
      if (ct != null && !ct.toLowerCase().contains("charset")) {
        response.setHeader("Content-Type", ct + ";charset=UTF-8");
      }
    }
  }
}