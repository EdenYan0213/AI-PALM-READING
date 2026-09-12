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
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口限流（IP+URI 维度）。单机内存实现：多实例部署时配额按实例数放大（TD §3 已知事项）。
 * Clock 可注入以便测试。
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

  private final boolean enabled;
  private final long windowMillis;
  private final int maxRequests;
  private final Clock clock;
  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  @org.springframework.beans.factory.annotation.Autowired
  public ApiRateLimitFilter(
      @Value("${app.rate-limit.enabled:true}") boolean enabled,
      @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${app.rate-limit.max-requests:90}") int maxRequests) {
    this(enabled, windowSeconds, maxRequests, Clock.systemUTC());
  }

  public ApiRateLimitFilter(boolean enabled, long windowSeconds, int maxRequests, Clock clock) {
    this.enabled = enabled;
    this.windowMillis = Math.max(1, windowSeconds) * 1000;
    this.maxRequests = Math.max(1, maxRequests);
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !enabled || !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String key = clientKey(request);
    long now = clock.millis();
    WindowCounter counter = counters.compute(key, (ignored, existing) -> {
      if (existing == null || now >= existing.resetAtMillis) {
        return new WindowCounter(now + windowMillis);
      }
      existing.count.incrementAndGet();
      return existing;
    });

    cleanupExpired(now);

    int count = counter.count.get();
    response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));
    response.setHeader("X-RateLimit-Reset", String.valueOf(counter.resetAtMillis / 1000));

    if (count > maxRequests) {
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String clientKey(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    String clientIp = forwardedFor == null || forwardedFor.isBlank()
        ? request.getRemoteAddr()
        : forwardedFor.split(",")[0].trim();
    return clientIp + ":" + request.getRequestURI();
  }

  private void cleanupExpired(long now) {
    if (counters.size() <= 1000) {
      return;
    }
    Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
    while (iterator.hasNext()) {
      if (now >= iterator.next().getValue().resetAtMillis) {
        iterator.remove();
      }
    }
  }

  static final class WindowCounter {
    private final long resetAtMillis;
    private final AtomicInteger count = new AtomicInteger();

    private WindowCounter(long resetAtMillis) {
      this.resetAtMillis = resetAtMillis;
    }
  }
}
