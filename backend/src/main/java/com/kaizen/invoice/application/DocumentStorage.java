package com.kaizen.invoice.application;

public interface DocumentStorage {
  void put(String key, byte[] data, String mime);

  byte[] get(String key);
}
