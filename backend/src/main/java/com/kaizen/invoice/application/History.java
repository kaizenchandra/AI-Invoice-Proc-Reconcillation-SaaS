package com.kaizen.invoice.application;

import com.kaizen.invoice.infrastructure.Database;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class History {
  private final Database db;

  public History(Database db) {
    this.db = db;
  }

  private UUID correlation() {
    String value = org.slf4j.MDC.get("correlationId");
    return value == null ? UUID.randomUUID() : UUID.fromString(value);
  }

  public void audit(
      UUID tenant, Object actor, String action, UUID resource, Object summary, String reason) {
    db.update(
        "insert into audit_events(id,tenant_id,actor,action,resource_id,summary,reason,correlation_id) values(?,?,?,?,?,?::jsonb,?,?)",
        UUID.randomUUID(),
        tenant,
        actor.toString(),
        action,
        resource,
        db.json(summary),
        reason,
        correlation());
  }

  public void notify(UUID tenant, UUID user, UUID resource, String title, String key) {
    db.update(
        "insert into notifications(id,tenant_id,user_id,resource_id,title,event_key) values(?,?,?,?,?,?) on conflict do nothing",
        UUID.randomUUID(),
        tenant,
        user,
        resource,
        title,
        key);
  }

  public void notifyTeam(UUID tenant, UUID resource, String title, String key) {
    for (var m :
        db.list(
            "select user_id from memberships where tenant_id=? and role in ('ORG_ADMIN','FINANCE_MANAGER','REVIEWER')",
            tenant)) notify(tenant, (UUID) m.get("user_id"), resource, title, key);
  }
}
