package com.mrdiet.common;

import org.springframework.http.HttpStatus;

/** Throw this anywhere to send {"error": "...", "message": "..."} with a status code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException badRequest(String msg)   { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg); }
    public static ApiException forbidden(String msg)    { return new ApiException(HttpStatus.FORBIDDEN, msg); }
    public static ApiException notFound(String msg)     { return new ApiException(HttpStatus.NOT_FOUND, msg); }
    public static ApiException conflict(String msg)     { return new ApiException(HttpStatus.CONFLICT, msg); }
    public static ApiException paymentRequired(String msg) { return new ApiException(HttpStatus.PAYMENT_REQUIRED, msg); }
    public static ApiException tooMany(String msg)      { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, msg); }
    public static ApiException upstream(String msg)     { return new ApiException(HttpStatus.BAD_GATEWAY, msg); }
}
