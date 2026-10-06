package com.appointmentplatform.shared.web;

import com.appointmentplatform.shared.ApiException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> business(ApiException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(title(exception.status()));
        var builder = ResponseEntity.status(exception.status());
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) {
            builder.header("Retry-After", "900");
        }
        return builder.body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        var errors = new LinkedHashMap<String, LinkedHashSet<String>>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.computeIfAbsent(error.getField(), ignored -> new LinkedHashSet<>())
                        .add(error.getDefaultMessage()));

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Bir veya daha fazla istek alanı geçersiz.");
        problem.setTitle("İstek doğrulanamadı");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "İstek gövdesi eksik veya geçerli JSON biçiminde değil.");
        problem.setTitle("İstek okunamadı");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "İşlem mevcut kayıtlarla çakışıyor.");
        problem.setTitle("Kayıt çakışması");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Geçersiz istek";
            case UNAUTHORIZED -> "Kimlik doğrulama başarısız";
            case NOT_FOUND -> "Kayıt bulunamadı";
            case CONFLICT -> "Kayıt çakışması";
            case TOO_MANY_REQUESTS -> "İstek sınırı aşıldı";
            default -> "İstek işlenemedi";
        };
    }
}
