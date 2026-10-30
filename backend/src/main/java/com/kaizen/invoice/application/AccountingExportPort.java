package com.kaizen.invoice.application;

import java.util.*;

public interface AccountingExportPort {
  byte[] export(List<Map<String, Object>> snapshots, List<String> fields);
}
