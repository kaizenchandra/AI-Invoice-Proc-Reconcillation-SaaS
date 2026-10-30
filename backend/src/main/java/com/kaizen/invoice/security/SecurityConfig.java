package com.kaizen.invoice.security;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.kaizen.invoice.infrastructure.Database;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer decimals() {
    return b -> b.serializerByType(BigDecimal.class, ToStringSerializer.instance);
  }

  @Bean
  UserDetailsService users(Database db) {
    return email ->
        db.optional(
                "select email,password_hash from app_users where email=?",
                email.toLowerCase(Locale.ROOT))
            .filter(r -> r.get("password_hash") != null)
            .map(
                r ->
                    User.withUsername(r.get("email").toString())
                        .password(r.get("password_hash").toString())
                        .roles("USER")
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations, Database db)
      throws Exception {
    var csrf = new HttpSessionCsrfTokenRepository();
    http.csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/v1/email/mailgun"))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/csrf",
                        "/api/v1/register",
                        "/api/v1/session",
                        "/api/v1/email/mailgun",
                        "/actuator/health/**",
                        "/error",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/v1/session")
                    .usernameParameter("email")
                    .successHandler((q, r, a) -> r.setStatus(204))
                    .failureHandler(
                        (q, r, e) -> {
                          r.setStatus(401);
                          r.setContentType("application/problem+json");
                          r.getWriter()
                              .write(
                                  "{\"detail\":\"Email or password is incorrect\",\"status\":401}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION")
                    .logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, r, x) -> {
                          r.setStatus(401);
                          r.setContentType("application/problem+json");
                          r.getWriter()
                              .write(
                                  "{\"detail\":\"Your session has expired. Sign in to continue.\",\"status\":401}");
                        })
                    .accessDeniedHandler(
                        (q, r, x) -> {
                          r.setStatus(403);
                          r.setContentType("application/problem+json");
                          r.getWriter()
                              .write(
                                  "{\"detail\":\"Access denied or security token expired. Refresh and try again.\",\"status\":403}");
                        }))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'self'")));
    if (registrations.getIfAvailable() != null)
      http.oauth2Login(
          o ->
              o.successHandler(
                  (request, response, authentication) -> {
                    var identity =
                        (org.springframework.security.oauth2.core.oidc.user.OidcUser)
                            authentication.getPrincipal();
                    if (!Boolean.TRUE.equals(identity.getEmailVerified())
                        || identity.getEmail() == null) {
                      request.getSession().invalidate();
                      response.sendError(403, "A verified identity-provider email is required");
                      return;
                    }
                    String issuer = identity.getIssuer().toString(),
                        subject = identity.getSubject(),
                        email = identity.getEmail().toLowerCase(Locale.ROOT);
                    var linked =
                        db.optional(
                            "select user_id from external_identities where issuer=? and subject=?",
                            issuer,
                            subject);
                    if (linked.isEmpty()) {
                      // Do not silently link a password account solely by an email assertion.
                      if (db.optional("select id from app_users where email=?", email)
                          .isPresent()) {
                        request.getSession().invalidate();
                        response.sendError(
                            409,
                            "An account already exists. An administrator must link this identity explicitly.");
                        return;
                      }
                      db.transaction(
                          () -> {
                            var id = java.util.UUID.randomUUID();
                            db.update(
                                "insert into app_users(id,email,name) values(?,?,?)",
                                id,
                                email,
                                java.util.Objects.toString(identity.getFullName(), email));
                            db.update(
                                "insert into external_identities values(?,?,?)",
                                issuer,
                                subject,
                                id);
                            return null;
                          });
                    }
                    response.sendRedirect("/");
                  }));
    return http.build();
  }
}
