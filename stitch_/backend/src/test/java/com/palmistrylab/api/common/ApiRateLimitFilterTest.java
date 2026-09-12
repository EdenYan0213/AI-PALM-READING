package com.palmistrylab.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计数语义：窗口内首个请求计数为 0，因此 maxRequests=2 时第 4 个请求被限流
 * （与线上一致：固定窗口"超过上限才拒绝"）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiRateLimitFilterTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

  private MutableClock clock;
  private ApiRateLimitFilter filter;
  private StringWriter responseBody;
  private int[] statusHolder;

  @BeforeEach
  void setUp() throws Exception {
    clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    // 60s 窗口最多 2 次放行
    filter = new ApiRateLimitFilter(true, 60, 2, clock);
    responseBody = new StringWriter();
    statusHolder = new int[]{200};
    when(request.getRequestURI()).thenReturn("/api/v1/palm/analyze");
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody, true));
    when(response.getStatus()).thenAnswer(invocation -> statusHolder[0]);
    org.mockito.Mockito.doAnswer(invocation -> {
      statusHolder[0] = invocation.getArgument(0);
      return null;
    }).when(response).setStatus(org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void allowsRequestsUpToLimitThenReturns429() throws Exception {
    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(200);

    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(responseBody.toString()).contains("RATE_LIMITED");
  }

  @Test
  void resetsWindowAfterWindowElapses() throws Exception {
    for (int i = 0; i < 5; i++) {
      filter.doFilter(request, response, chain);
    }
    assertThat(response.getStatus()).isEqualTo(429);

    clock.advance(Duration.ofSeconds(61));
    // 模拟新一轮响应对象：mock 的 status 不会像真实容器那样自动回到 200
    statusHolder[0] = 200;
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void nonApiPathsBypassTheFilter() throws Exception {
    when(request.getRequestURI()).thenReturn("/index.html");

    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);
    filter.doFilter(request, response, chain);

    verify(chain, org.mockito.Mockito.times(5)).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void setsRateLimitHeaders() throws Exception {
    filter.doFilter(request, response, chain);

    verify(response).setHeader("X-RateLimit-Limit", "2");
    verify(response).setHeader("X-RateLimit-Remaining", "2");
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant start) {
      this.instant = start;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
