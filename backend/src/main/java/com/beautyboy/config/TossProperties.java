package com.beautyboy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 연동 설정.
 *
 * <p>시크릿 키의 기본값을 코드에 적지 않는다 — 적는 순간 그것이 커밋된 시크릿이다.
 * 환경변수 {@code TOSS_SECRET_KEY}로만 주입하고, 없으면 앱이 뜨지 않게 둔다(뜨더라도 결제에서 실패한다).
 *
 * <p>baseUrl은 테스트 결제 서버 주소라 소스에 둬도 시크릿이 아니다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String baseUrl) {

    public TossProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.tosspayments.com";
        }
    }
}
