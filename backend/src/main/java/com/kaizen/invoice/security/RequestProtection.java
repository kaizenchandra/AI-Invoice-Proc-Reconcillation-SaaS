package com.kaizen.invoice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestProtection extends OncePerRequestFilter {
  private record Window(long minute, int count) {}

  private final Map<String, Window> limits = new ConcurrentHashMap<>();

  protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
      throws ServletException, IOException {
    String correlation = UUID.randomUUID().toString();
    r.setHeader("X-Correlation-ID", correlation);
    String path = q.getRequestURI();
    if ("POST".equals(q.getMethod())
        && (path.equals("/api/v1/session") || path.equals("/api/v1/register"))) {
      long minute = System.currentTimeMillis() / 60000;
      String key = q.getRemoteAddr();
      limits.entrySet().removeIf(e -> e.getValue().minute < minute - 1);
      Window w =
          limits.compute(
              key,
              (k, v) ->
                  v == null || v.minute != minute
                      ? new Window(minute, 1)
                      : new Window(minute, v.count + 1));
      if (w.count > 30) {
        r.setStatus(429);
        r.setHeader("Retry-After", "60");
        r.setContentType("application/problem+json");
        r.getWriter()
            .write("{\"detail\":\"Too many sign-in attempts. Try again shortly.\",\"status\":429}");
        return;
      }
    }
    org.slf4j.MDC.put("correlationId", correlation);
    try {
      chain.doFilter(q, r);
    } finally {
      org.slf4j.MDC.remove("correlationId");
    }
  }
}
