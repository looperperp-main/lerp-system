package com.l.erp.common.exception.custom;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BussinessException extends RuntimeException{
    private final HttpStatus status;

    public BussinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BussinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
