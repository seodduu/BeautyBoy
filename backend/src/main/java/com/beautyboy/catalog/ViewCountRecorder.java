package com.beautyboy.catalog;

/**
 * 상품 상세 조회수 기록 경계.
 *
 * <p>왜 인터페이스인가: 조회수는 상세를 볼 때마다 쓰기가 나가는, 이 서비스에서 쓰기 경합이 가장
 * 심한 카운터다. 그래서 Redis INCR로 옮길 값어치가 있다. 그런데 Redis를 필수로 만들면
 * "docker compose 없이 백엔드만 띄우기"가 깨진다 — 구현을 갈라 설정으로 고른다.
 *
 * <p>기본값은 DB 즉시 증가({@link DbViewCountRecorder})다. Redis 구현은
 * {@code beautyboy.view-count.redis=true}일 때만 뜬다.
 */
public interface ViewCountRecorder {

    /** 조회 1회 기록. <b>실패해도 예외를 밖으로 던지지 않는다</b> — 조회수 때문에 상세가 500이 되면 안 된다. */
    void record(Long goodsNo);
}
