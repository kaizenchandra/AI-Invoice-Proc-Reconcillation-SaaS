package com.kaizen.invoice.security;

import com.kaizen.invoice.domain.Problem;
import com.kaizen.invoice.infrastructure.Database;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class Access {
  public enum Permission {
    READ,
    UPLOAD,
    EDIT,
    MASTER,
    APPROVE,
    EXPORT,
    ADMIN,
    RESOLVE
  }

  public record Scope(UUID tenant, UUID user, String role) {}

  private final Database db;

  public Access(Database db) {
    this.db = db;
  }

  public Map<String, Object> user() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    Problem.require(
        auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()),
        401,
        "Sign in to continue");
    if (auth.getPrincipal() instanceof OidcUser oidc)
      return db.one(
          "select u.id,u.email,u.name from app_users u join external_identities e on e.user_id=u.id where e.issuer=? and e.subject=?",
          oidc.getIssuer().toString(),
          oidc.getSubject());
    return db.one(
        "select id,email,name from app_users where email=?",
        auth.getName().toLowerCase(Locale.ROOT));
  }

  public Scope require(UUID tenant, Permission permission) {
    UUID user = (UUID) user().get("id");
    var membership =
        db.optional("select role from memberships where tenant_id=? and user_id=?", tenant, user)
            .orElseThrow(() -> new Problem(403, "Organization access denied"));
    String role = membership.get("role").toString();
    Problem.require(
        permissions(role).contains(permission),
        403,
        "Your organization role does not permit this action");
    return new Scope(tenant, user, role);
  }

  public static Set<Permission> permissions(String role) {
    return switch (role) {
      case "PLATFORM_ADMIN", "ORG_ADMIN" -> EnumSet.allOf(Permission.class);
      case "FINANCE_MANAGER" ->
          EnumSet.of(
              Permission.READ,
              Permission.UPLOAD,
              Permission.EDIT,
              Permission.MASTER,
              Permission.APPROVE,
              Permission.EXPORT,
              Permission.RESOLVE);
      case "PROCESSOR" ->
          EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT, Permission.MASTER);
      case "REVIEWER" -> EnumSet.of(Permission.READ, Permission.EDIT, Permission.RESOLVE);
      case "APPROVER" -> EnumSet.of(Permission.READ, Permission.APPROVE);
      default -> EnumSet.of(Permission.READ);
    };
  }
}
