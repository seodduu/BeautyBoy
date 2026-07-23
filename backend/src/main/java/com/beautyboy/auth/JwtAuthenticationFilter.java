package com.beautyboy.auth;

import com.beautyboy.common.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer {accessToken} 헤더를 파싱해 SecurityContext에 인증 정보를 세팅한다.
 * 토큰이 없거나 유효하지 않으면(만료·위조) SecurityContext를 세팅하지 않고 체인을 계속 진행시켜,
 * Spring Security의 인증 진입점이 401을 내도록 위임한다. (필터는 DispatcherServlet 밖이라
 * BusinessException을 GlobalExceptionHandler가 잡지 못하므로 여기서 직접 던지지 않는다.)
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public JwtAuthenticationFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                TokenProvider.TokenClaims claims = tokenProvider.parse(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()));
                var authentication = new UsernamePasswordAuthenticationToken(claims.memberId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
