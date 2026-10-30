package com.kaizen.invoice.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class Database {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;

  public Database(JdbcTemplate jdbc, ObjectMapper mapper, TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.tx = tx;
  }

  public ObjectMapper mapper() {
    return mapper;
  }

  public <T> T transaction(Supplier<T> work) {
    return tx.execute(s -> work.get());
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.query(sql, (r, n) -> row(r), args);
  }

  public Optional<Map<String, Object>> optional(String sql, Object... args) {
    return list(sql, args).stream().findFirst();
  }

  public Map<String, Object> one(String sql, Object... args) {
    return optional(sql, args)
        .orElseThrow(() -> new com.kaizen.invoice.domain.Problem(404, "Record not found"));
  }

  public String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid structured data", e);
    }
  }

  public <T> T convert(Object value, Class<T> type) {
    return mapper.convertValue(value, type);
  }

  private Map<String, Object> row(ResultSet r) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    var md = r.getMetaData();
    for (int i = 1; i <= md.getColumnCount(); i++) {
      Object v = r.getObject(i);
      if (v != null && "jsonb".equals(md.getColumnTypeName(i)))
        try {
          v = mapper.readValue(v.toString(), Object.class);
        } catch (Exception e) {
          throw new SQLException(e);
        }
      if (v instanceof Timestamp t) v = t.toInstant().toString();
      if (v instanceof java.sql.Date d) v = d.toLocalDate().toString();
      result.put(md.getColumnLabel(i), v);
    }
    return result;
  }
}
