package com.beautyboy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 연동 설정.
 *
 * <p>시크릿 키의 기본값을 코드에 적지 않는다 — 적는 순간 그것이 커밋된 시크릿이다.
 * 환경변수 {@code TOSS_SECRET_KEY}로만 주입하고, 없으면 앱이 뜨지 않게 둔다(뜨더라도 결제에서 실패한다).
 *
 * <p>baseUrl은 테스트 결제 서버 주소라 소스에 둬도 시크릿이 아니다.
 *
 * <p>타임아웃 기본값의 근거: 재고 차감이 토스 HTTP 호출 동안 goods_option 행 락을 쥔다
 * (Wave 5 설계). connect 3s + read 10s = 락 보유 최악 ~13s로 innodb_lock_wait_timeout(50s)
 * 훨씬 이전에 풀린다. 토스 테스트 API 정상 응답은 1s 미만이라 오탐 여지 없음.
 * 프로퍼티로 둔 것은 운영 튜닝용이 아니라 테스트에서 짧은 값을 주입해 타임아웃 발화를
 * 실제로 증명하기 위해서다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String baseUrl,
                             Duration connectTimeout, Duration readTimeout) {

    public TossProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.tosspayments.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
