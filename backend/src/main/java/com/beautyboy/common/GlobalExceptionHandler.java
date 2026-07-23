package com.beautyboy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
     * 요청 파싱/바인딩 단계에서 나는 표준 Spring MVC 예외들.
     * 클라이언트 잘못이므로 500(캐치올)이 아니라 400 INVALID_INPUT으로 매핑한다.
     * - HttpMessageNotReadableException: 깨진 JSON 바디
     * - MethodArgumentTypeMismatchException: 경로/쿼리 파라미터 타입 불일치 (예: 숫자 자리에 문자열)
     * - MissingServletRequestParameterException: 필수 쿼리 파라미터 누락
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestException(Exception e) {
        log.debug("잘못된 요청입니다", e);
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    /**
     * 지원하지 않는 HTTP 메서드로 호출한 경우. 405로 응답해야 하는데 캐치올에 잡히면 500이 된다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    /**
     * Content-Type 누락/미지원. 415로 응답해야 하는데 캐치올에 잡히면 500이 된다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    /**
     * 컬럼 길이 초과, 유니크 제약 위반 등 DB 무결성 제약을 어긴 경우.
     * DTO 검증을 다 통과했더라도 스키마 제약과 충돌할 수 있으므로 409로 응답한다
     * (클라이언트 요청 자체가 잘못된 게 아니라 현재 서버 상태와 충돌하는 것이므로 400이 아닌 409).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약 위반이 발생했습니다", e);
        ErrorCode errorCode = ErrorCode.DATA_CONFLICT;
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage(), null));
    }

    /**
     * 인가(403)/인증(401) 예외는 Spring Security의 ExceptionTranslationFilter가 처리해야 한다.
     * 이 클래스의 Exception 캐치올(아래)이 컨트롤러 호출 스택에서 던져진 AccessDeniedException을
     * 먼저 가로채면 @PreAuthorize로 거부된 요청이 403이 아니라 500으로 나가버린다.
     * 여기서 그대로 다시 던져 Security 필터 체인까지 전파되게 한다.
     * (AuthorizationDeniedException은 Spring Security 6에서 AccessDeniedException의 하위 타입이라
     * 별도로 명시하지 않아도 함께 잡힌다.)
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException e) {
        throw e;
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
     * BusinessException/검증 예외/위의 표준 MVC 예외처럼 명시적으로 다루지 않은 예외의 최종 안전망.
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
