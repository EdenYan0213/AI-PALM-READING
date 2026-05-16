package com.palmistrylab.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("code", "BAD_REQUEST");
    payload.put("message", ex.getMessage());
    return ResponseEntity.badRequest().body(payload);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("code", "VALIDATION_ERROR");
    payload.put("message", "请求参数不完整或格式错误");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("code", "INTERNAL_ERROR");
    payload.put("message", "服务繁忙，请稍后再试: " + ex.getClass().getSimpleName() + ": " + (ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 200)) : "null"));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
  }
}
