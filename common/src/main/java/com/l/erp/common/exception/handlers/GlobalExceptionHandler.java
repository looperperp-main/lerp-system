package com.l.erp.common.exception.handlers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.custom.UserLockedException;
import com.l.erp.common.exception.dto.StandardError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.AccessDeniedException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<StandardError> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        StandardError err = StandardError.builder()
                .timestamp(Instant.now())
                .status(e.getStatusCode().value())
                .error(error)
                .message(e.getReason())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(e.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardError> handleGenericException(Exception e, HttpServletRequest request) {
        if(log.isErrorEnabled()){
            log.error("Erro não tratado em {} {} — {}",
                    request.getMethod(), request.getRequestURI(), e.toString(), e);
        }
        StandardError err = StandardError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("Ocorreu um erro inesperado. Tente novamente ou contate o suporte.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandardError> handleBussinessException(BusinessException e, HttpServletRequest request) {
        StandardError err = StandardError.builder()
                .timestamp(Instant.now())
                .status(e.getStatus().value())
                .error("Erro de Negocio")
                .message(e.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(e.getStatus()).body(err);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<StandardError> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        StandardError err = StandardError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access Denied")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
    }

    @ExceptionHandler(UserLockedException.class)
    public ResponseEntity<Map<String, Object>> handleUserLocked(UserLockedException ex) {
        Instant lockedUntil = ex.getLockedUntil();
        Duration remaining = Duration.between(Instant.now(), lockedUntil);

        long minutes = remaining.toMinutes();
        long seconds = remaining.minusMinutes(minutes).getSeconds();

        String tempoRestante = minutes > 0
                ? String.format("%d minutos e %d segundos", minutes, seconds)
                : String.format("%d segundos", seconds);

        String lockedUntilFormatted = FORMATTER.format(lockedUntil);

        Map<String, Object> body = Map.of(
                "status", HttpStatus.LOCKED.value(),
                "error", "USER_LOCKED",
                "message", String.format(
                        "Usuário travado até %s, falta %s. Contate um administrador.",
                        lockedUntilFormatted,
                        tempoRestante
                ),
                "lockedUntil", lockedUntil.toString()
        );

        return ResponseEntity.status(HttpStatus.LOCKED).body(body);
    }
}
