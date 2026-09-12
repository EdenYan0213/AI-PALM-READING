package com.palmistrylab.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 运营指标端点的访问令牌门禁：配置 app.admin.token（非空）时，
 * /metrics/summary 必须携带匹配的 X-Admin-Token 请求头，否则 401。
 * 留空表示不启用鉴权（仅建议本地开发）。
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

  private static final List<String> PROTECTED_PATHS = List.of("/api/v1/metrics/summary");

  private final byte[] adminToken;

  public AdminTokenFilter(@Value("${app.admin.token:}") String adminToken) {
    this.adminToken = adminToken == null ? new byte[0] : adminToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (adminToken.length == 0) {
      return true;
    }
    return !PROTECTED_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String provided = request.getHeader("X-Admin-Token");
    boolean authorized = provided != null && MessageDigest.isEqual(
        provided.getBytes(StandardCharsets.UTF_8), adminToken);
    if (!authorized) {
      response.setStatus(401);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"需要有效的 X-Admin-Token 访问令牌\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
