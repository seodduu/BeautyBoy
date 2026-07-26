package com.beautyboy.auth;

import com.beautyboy.auth.dto.LoginRequest;
import com.beautyboy.auth.dto.RefreshResponse;
import com.beautyboy.auth.dto.TokenResponse;
import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok(new TokenResponse(result.accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AuthService.RefreshResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok(new RefreshResponse(result.accessToken(), result.member())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(ApiResponse.ok(null));
    }

    private ResponseCookie refreshCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtProperties.getRefreshExpDays()))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
