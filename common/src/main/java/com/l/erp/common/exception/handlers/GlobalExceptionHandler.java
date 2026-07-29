package com.l.erp.common.exception.handlers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.custom.UserLockedException;
import com.l.erp.common.exception.dto.StandardError;
import com.l.erp.common.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler global de exceções — padrão de erros do projeto (2026-07-06):
 * <ul>
 *   <li>Status correto: erro de cliente (4xx) nunca vira 500.</li>
 *   <li>Log por classe: 4xx → {@code WARN}, uma linha, sem stacktrace; 5xx → {@code ERROR} com stacktrace.</li>
 *   <li>Mensagens em PT-BR; 5xx nunca vaza detalhe interno pro cliente.</li>
 *   <li>Todo corpo e toda linha de log carregam o {@code correlationId} (colável no Rastreador).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /** correlationId da requisição atual (populado pelo CorrelationIdFilter). */
    private static String correlationId() {
        return MDC.get(Constants.MDC_CORRELATION_ID);
    }

    /** Monta o corpo padrão já com correlationId. */
    private static StandardError body(int status, String error, String message, HttpServletRequest request) {
        return StandardError.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .correlationId(correlationId())
                .build();
    }

    /** Uma linha só, sem stacktrace — para erros de cliente (4xx) e outros esperados. */
    private static void logClientError(int status, HttpServletRequest request, String message) {
        log.warn("[corr={}] {} {} {} — {}", correlationId(), status, request.getMethod(), request.getRequestURI(), message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StandardError> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        logClientError(HttpStatus.NOT_FOUND.value(), request, "recurso inexistente");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(HttpStatus.NOT_FOUND.value(), "Não encontrado", "Recurso não encontrado.", request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<StandardError> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        HttpStatusCode statusCode = e.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.getReasonPhrase() : "Erro";
        String message = e.getReason();
        if (statusCode.is5xxServerError()) {
            log.error("[corr={}] {} {} {} — {}", correlationId(), statusCode.value(), request.getMethod(), request.getRequestURI(), message, e);
        } else {
            logClientError(statusCode.value(), request, message);
        }
        return ResponseEntity.status(statusCode).body(body(statusCode.value(), error, message, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardError> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        logClientError(HttpStatus.BAD_REQUEST.value(), request, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(HttpStatus.BAD_REQUEST.value(), "Erro de validação", message, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandardError> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        // Param tipado (UUID/Long/enum/data) mal-formado é erro do cliente → 400, nunca 500.
        String message = e.getName() + ": valor inválido";
        logClientError(HttpStatus.BAD_REQUEST.value(), request, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(HttpStatus.BAD_REQUEST.value(), "Erro de validação", message, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandardError> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        // Corpo ausente, JSON quebrado ou campo com tipo/formato errado (ex.: data em dd/MM/yyyy).
        // É erro do cliente → 400. Sem detalhe do parser no corpo: pode citar trecho do payload.
        logClientError(HttpStatus.BAD_REQUEST.value(), request, "corpo ilegível: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(HttpStatus.BAD_REQUEST.value(), "Erro de validação",
                        "Corpo da requisição ausente ou mal-formado.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<StandardError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        logClientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), request, "content-type não suportado: " + e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(body(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Formato não suportado",
                        "Formato de conteúdo não suportado para este recurso.", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StandardError> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logClientError(HttpStatus.METHOD_NOT_ALLOWED.value(), request, "método não suportado: " + e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(body(HttpStatus.METHOD_NOT_ALLOWED.value(), "Método não permitido",
                        "Método HTTP não suportado para este recurso.", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardError> handleGenericException(Exception e, HttpServletRequest request) {
        // Único ponto que loga stacktrace — bug de verdade (5xx).
        log.error("[corr={}] 500 {} {} — {}", correlationId(), request.getMethod(), request.getRequestURI(), e.toString(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Erro interno",
                        "Ocorreu um erro inesperado. Tente novamente ou contate o suporte.", request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandardError> handleBussinessException(BusinessException e, HttpServletRequest request) {
        logClientError(e.getStatus().value(), request, e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(body(e.getStatus().value(), "Erro de Negócio", e.getMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<StandardError> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        logClientError(HttpStatus.FORBIDDEN.value(), request, "acesso negado");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(HttpStatus.FORBIDDEN.value(), "Acesso negado", "Acesso negado.", request));
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
