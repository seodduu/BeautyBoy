package com.beautyboy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<Map<String, String>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDetail)
                .collect(Collectors.toList());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.name(), ErrorCode.INVALID_INPUT.getMessage(), fieldErrors));
    }

    /**
     * 정적 리소스 핸들러가 마지막 폴백으로 매핑되어 있어, 존재하지 않는 GET 경로는
     * DispatcherServlet의 "핸들러 없음"이 아니라 이 예외로 떨어진다.
     * 아래 Exception catch-all보다 먼저(더 구체적으로) 잡지 않으면 마땅히 404여야 할
     * 요청이 500으로 잘못 응답된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        ErrorCode errorCode = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    /**
     * BusinessException/검증 예외처럼 명시적으로 다루지 않은 예외의 최종 안전망.
     * 이게 없으면 예외가 서블릿 컨테이너까지 새어나가 /error로 포워딩되고,
     * SecurityConfig가 /error를 anyRequest().authenticated()로 막고 있어
     * 실제로는 500이어야 할 응답이 401(UNAUTHORIZED)로 둔갑해버린다.
     * 여기서 잡아 앱의 에러 계약(ErrorResponse)대로 500을 내려준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    private Map<String, String> toFieldErrorDetail(FieldError fieldError) {
        return Map.of(
                "field", fieldError.getField(),
                "reason", fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage()
        );
    }
}
