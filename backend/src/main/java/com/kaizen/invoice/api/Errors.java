package com.kaizen.invoice.api;

import com.kaizen.invoice.domain.Problem;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(Problem.class)
  ResponseEntity<ProblemDetail> problem(Problem e) {
    return response(e.status, e.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return response(
        400, "Invalid request. Check required values, decimal amounts, dates and identifiers.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> fields(MethodArgumentNotValidException e) {
    var detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the highlighted fields.");
    var errors = new java.util.LinkedHashMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    detail.setProperty("fields", errors);
    return ResponseEntity.badRequest().body(detail);
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> tooLarge(Exception e) {
    return response(413, "File exceeds the 20 MB upload limit.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return response(
        409,
        "This action conflicts with an existing record or a linked resource. Refresh the record and retry.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LoggerFactory.getLogger(Errors.class).error("Request failed: {}", e.getClass().getSimpleName());
    return response(
        500,
        "The operation could not be completed. Your saved records are retained; retry or contact your administrator.");
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail) {
    return ResponseEntity.status(status)
        .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail));
  }
}
