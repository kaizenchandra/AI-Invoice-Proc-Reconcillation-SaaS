package com.kaizen.invoice.api;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.application.History;
import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.Database;
import com.kaizen.invoice.security.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
  private final Database db;
  private final Access access;
  private final PasswordEncoder passwords;
  private final History history;

  public IdentityController(
      Database db, Access access, PasswordEncoder passwords, History history) {
    this.db = db;
    this.access = access;
    this.passwords = passwords;
    this.history = history;
  }

  public record Registration(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 2, max = 100) String name,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "header", token.getHeaderName());
  }

  @PostMapping("/register")
  public Map<String, Object> register(@Valid @RequestBody Registration r) {
    db.update(
        "insert into app_users(id,email,name,password_hash) values(?,?,?,?)",
        UUID.randomUUID(),
        r.email().toLowerCase(Locale.ROOT),
        r.name(),
        passwords.encode(r.password()));
    return Map.of("registered", true);
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return access.user();
  }

  @GetMapping("/organizations")
  public List<Map<String, Object>> organizations() {
    return db.list(
        "select o.*,m.role from organizations o join memberships m on o.id=m.tenant_id where m.user_id=? order by o.name",
        access.user().get("id"));
  }

  public record Organization(
      @NotBlank @Size(max = 160) String name,
      @NotBlank String state,
      String gstin,
      @NotBlank String address,
      @NotBlank String financialYear,
      String pan) {}

  public static Map<String, Object> defaults() {
    return Map.of(
        "roundingTolerance",
        "0.50",
        "priceTolerance",
        "0.00",
        "separationOfDuties",
        true,
        "requirePo",
        true,
        "reviewThreshold",
        "100000.00",
        "requiredFields",
        List.of(),
        "exportFields",
        List.of(
            "invoiceNumber",
            "invoiceDate",
            "supplierName",
            "supplierGstin",
            "taxableAmount",
            "cgst",
            "sgst",
            "igst",
            "total",
            "currency"));
  }

  @PostMapping("/organizations")
  public Map<String, Object> create(@Valid @RequestBody Organization o) {
    validate(o);
    UUID id = UUID.randomUUID(), user = (UUID) access.user().get("id");
    return db.transaction(
        () -> {
          db.update(
              "insert into organizations(id,name,profile,settings,intake_alias) values(?,?,?::jsonb,?::jsonb,?)",
              id,
              o.name(),
              db.json(o),
              db.json(defaults()),
              "invoices-" + id);
          db.update("insert into memberships values(?,?,?)", id, user, "ORG_ADMIN");
          history.audit(id, user, "ORGANIZATION_CREATED", id, Map.of("name", o.name()), null);
          return db.one("select * from organizations where id=?", id);
        });
  }

  @PatchMapping("/organizations/{id}")
  public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody Organization o) {
    var s = access.require(id, Access.Permission.ADMIN);
    validate(o);
    return db.transaction(
        () -> {
          db.update(
              "update organizations set name=?,profile=?::jsonb,policy_version=policy_version+1 where id=?",
              o.name(),
              db.json(o),
              id);
          history.audit(id, s.user(), "ORGANIZATION_UPDATED", id, o, null);
          return db.one("select * from organizations where id=?", id);
        });
  }

  private void validate(Organization o) {
    require(
        o.state().matches("[0-9]{2}") && o.financialYear().matches("20[0-9]{2}-20[0-9]{2}"),
        400,
        "Use a two-digit state and YYYY-YYYY financial year");
    require(
        o.gstin() == null || o.gstin().isBlank() || Gstin.valid(o.gstin()), 400, "Invalid GSTIN");
  }

  @GetMapping("/organizations/{id}/members")
  public List<Map<String, Object>> members(@PathVariable UUID id) {
    access.require(id, Access.Permission.READ);
    return db.list(
        "select u.id,u.email,u.name,m.role from memberships m join app_users u on u.id=m.user_id where m.tenant_id=? order by u.name",
        id);
  }

  public record Invite(@Email @NotBlank String email, @NotBlank String role) {}

  @PostMapping("/organizations/{id}/invitations")
  public Map<String, Object> invite(@PathVariable UUID id, @Valid @RequestBody Invite input) {
    var s = access.require(id, Access.Permission.ADMIN);
    require(
        Set.of("ORG_ADMIN", "FINANCE_MANAGER", "PROCESSOR", "REVIEWER", "APPROVER", "AUDITOR")
            .contains(input.role()),
        400,
        "Invalid organization role");
    String token = UUID.randomUUID() + "-" + UUID.randomUUID();
    UUID invite = UUID.randomUUID();
    return db.transaction(
        () -> {
          db.update(
              "insert into invitations(id,tenant_id,email,role,token_hash,expires_at) values(?,?,?,?,?,?)",
              invite,
              id,
              input.email().toLowerCase(Locale.ROOT),
              input.role(),
              hash(token),
              java.sql.Timestamp.from(Instant.now().plusSeconds(604800)));
          history.audit(
              id,
              s.user(),
              "MEMBER_INVITED",
              invite,
              Map.of("email", input.email(), "role", input.role()),
              null);
          return Map.of(
              "token",
              token,
              "expiresInDays",
              7,
              "delivery",
              "Share this invitation link securely with the recipient. Email delivery is not configured.");
        });
  }

  public record Accept(@NotBlank String token) {}

  @PostMapping("/invitations/accept")
  public Map<String, Object> accept(@Valid @RequestBody Accept input) {
    var user = access.user();
    return db.transaction(
        () -> {
          var invitation =
              db.one(
                  "select * from invitations where token_hash=? and accepted_at is null and expires_at>now() for update",
                  hash(input.token()));
          require(
              invitation.get("email").equals(user.get("email")),
              403,
              "Sign in using the invited email address");
          db.update(
              "insert into memberships values(?,?,?) on conflict do nothing",
              invitation.get("tenant_id"),
              user.get("id"),
              invitation.get("role"));
          db.update("update invitations set accepted_at=now() where id=?", invitation.get("id"));
          history.audit(
              (UUID) invitation.get("tenant_id"),
              user.get("id"),
              "INVITATION_ACCEPTED",
              (UUID) invitation.get("id"),
              Map.of(),
              null);
          return Map.of("tenantId", invitation.get("tenant_id"));
        });
  }

  @PatchMapping("/organizations/{id}/members/{user}")
  public void role(@PathVariable UUID id, @PathVariable UUID user, @RequestBody Invite input) {
    var s = access.require(id, Access.Permission.ADMIN);
    require(!s.user().equals(user), 409, "You cannot change your own role");
    require(
        Set.of("ORG_ADMIN", "FINANCE_MANAGER", "PROCESSOR", "REVIEWER", "APPROVER", "AUDITOR")
            .contains(input.role()),
        400,
        "Invalid role");
    db.transaction(
        () -> {
          require(
              db.update(
                      "update memberships set role=? where tenant_id=? and user_id=?",
                      input.role(),
                      id,
                      user)
                  == 1,
              404,
              "Membership not found");
          history.audit(id, s.user(), "ROLE_CHANGED", user, Map.of("role", input.role()), null);
          return null;
        });
  }

  @GetMapping("/organizations/{id}/business-units")
  public List<Map<String, Object>> units(@PathVariable UUID id) {
    access.require(id, Access.Permission.READ);
    return db.list("select * from business_units where tenant_id=?", id);
  }

  @PostMapping("/organizations/{id}/business-units")
  public void unit(@PathVariable UUID id, @Valid @RequestBody Organization o) {
    var s = access.require(id, Access.Permission.ADMIN);
    validate(o);
    db.transaction(
        () -> {
          UUID unit = UUID.randomUUID();
          db.update(
              "insert into business_units(id,tenant_id,name,gstin,state,address) values(?,?,?,?,?,?)",
              unit,
              id,
              o.name(),
              o.gstin(),
              o.state(),
              o.address());
          history.audit(id, s.user(), "BUSINESS_UNIT_CREATED", unit, o, null);
          return null;
        });
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
