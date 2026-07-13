package com.fitcoach.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void business_exception_uses_matching_http_status() {
        var response = handler.handleBusiness(new BusinessException(404, "missing"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(404);
    }

    @Test
    void unknown_business_code_falls_back_to_bad_request() {
        var response = handler.handleBusiness(new BusinessException(999, "invalid"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(999);
    }
}
