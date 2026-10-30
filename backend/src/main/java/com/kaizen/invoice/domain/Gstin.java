package com.kaizen.invoice.domain;

import java.util.Locale;

public final class Gstin {
  private Gstin() {}

  public static boolean valid(String value) {
    if (value == null) return false;
    String gst = value.toUpperCase(Locale.ROOT);
    if (!gst.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")) return false;
    int state = Integer.parseInt(gst.substring(0, 2));
    if (state < 1 || (state > 38 && state != 97 && state != 99)) return false;
    int sum = 0, factor = 2;
    for (int i = 13; i >= 0; i--) {
      int n = Character.digit(gst.charAt(i), 36) * factor;
      sum += n / 36 + n % 36;
      factor = 3 - factor;
    }
    return Character.digit(gst.charAt(14), 36) == (36 - sum % 36) % 36;
  }
}
