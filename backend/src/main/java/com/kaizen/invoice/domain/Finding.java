package com.kaizen.invoice.domain;

public record Finding(
    String ruleId,
    String ruleVersion,
    String severity,
    String field,
    String actual,
    String expected,
    String explanation,
    String resolution,
    boolean blocking) {
  public static Finding block(
      String rule, String field, Object actual, Object expected, String explanation) {
    return new Finding(
        rule,
        "1.0",
        "BLOCKING",
        field,
        String.valueOf(actual),
        String.valueOf(expected),
        explanation,
        "Correct the source data and revalidate.",
        true);
  }
}
