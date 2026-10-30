package com.kaizen.invoice.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InvoiceData(
    String invoiceNumber,
    String invoiceDate,
    String dueDate,
    String financialYear,
    String documentType,
    String currency,
    UUID supplierId,
    String supplierName,
    String supplierGstin,
    String supplierPan,
    String supplierAddress,
    String supplierState,
    String supplierContact,
    String buyerName,
    String buyerGstin,
    String billingAddress,
    String shippingAddress,
    String placeOfSupply,
    String poNumber,
    String paymentTerms,
    String irn,
    String originalInvoiceNumber,
    String taxContext,
    Boolean einvoiceRequired,
    BigDecimal grossAmount,
    BigDecimal discount,
    BigDecimal taxableAmount,
    BigDecimal cgst,
    BigDecimal sgst,
    BigDecimal igst,
    BigDecimal cess,
    BigDecimal charges,
    BigDecimal roundOff,
    BigDecimal total,
    List<Line> lines) {
  public record Line(
      String code,
      String description,
      String hsn,
      BigDecimal quantity,
      String unit,
      BigDecimal price,
      BigDecimal discount,
      BigDecimal taxableAmount,
      BigDecimal taxRate,
      BigDecimal cgst,
      BigDecimal sgst,
      BigDecimal igst,
      BigDecimal cess,
      BigDecimal total) {}

  public InvoiceData {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
