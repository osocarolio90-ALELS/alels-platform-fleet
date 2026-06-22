package com.alels.backend.serverops.shared.exception;

public record StandardErrorResponse(boolean success, String code, String message, String timestamp) {}
