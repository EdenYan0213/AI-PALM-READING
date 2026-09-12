package com.palmistrylab.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminTokenFilterTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

  @Test
  void passesThroughWhenTokenNotConfigured() throws Exception {
    AdminTokenFilter filter = new AdminTokenFilter("");
    when(request.getRequestURI()).thenReturn("/api/v1/metrics/summary");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void rejectsMetricsSummaryWithoutToken() throws Exception {
    AdminTokenFilter filter = new AdminTokenFilter("secret-token");
    StringWriter body = new StringWriter();
    when(request.getRequestURI()).thenReturn("/api/v1/metrics/summary");
    when(request.getHeader("X-Admin-Token")).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body, true));

    filter.doFilter(request, response, chain);

    verify(response).setStatus(401);
    verify(chain, never()).doFilter(request, response);
    assertThat(body.toString()).contains("UNAUTHORIZED");
  }

  @Test
  void allowsMetricsSummaryWithMatchingToken() throws Exception {
    AdminTokenFilter filter = new AdminTokenFilter("secret-token");
    when(request.getRequestURI()).thenReturn("/api/v1/metrics/summary");
    when(request.getHeader("X-Admin-Token")).thenReturn("secret-token");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void otherApiPathsAreNotGated() throws Exception {
    AdminTokenFilter filter = new AdminTokenFilter("secret-token");
    when(request.getRequestURI()).thenReturn("/api/v1/events/track");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }
}
