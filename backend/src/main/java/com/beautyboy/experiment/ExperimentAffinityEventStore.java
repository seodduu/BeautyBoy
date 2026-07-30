package com.beautyboy.experiment;

import com.beautyboy.experiment.dto.AffinityNextStepRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 상태 있는 서버형(B') 실험의 이벤트 저장소 — "서버형 개인화라면 생겼을 쓰기 경로"의 비용을 재는
 * 유일한 목적으로 존재한다(무상태 B는 이벤트를 요청 바디로 받아 이 경로가 없다).
 *
 * <p><b>Flyway를 쓰지 않는 이유</b>: 마이그레이션 스크립트는 공유 계약이고, 이 테이블은 실험이
 * 끝나면 DROP되는 일회용이다. {@code @Profile("experiment")} 기동 시에만
 * {@code CREATE TABLE IF NOT EXISTS}로 만들며, 기존 테이블·마이그레이션 체인을 건드리지 않는다.
 * 측정 후 정리는 수동 {@code DROP TABLE experiment_affinity_event}다 (conditions.md §6-4).
 *
 * <p>링버퍼 50건 의미는 읽기 쪽 {@code LIMIT}으로 재현한다 — 쓰기 시 오래된 행을 지우면 화면마다
 * DELETE가 추가되는데, 클라이언트의 {@code slice(-50)}은 쓰기 비용이 0이라 비교가 불공정해진다.
 * 대신 읽기가 최신 50건만 집는다(집계는 합산이라 순서 무관).
 */
@Component
@Profile("experiment")
public class ExperimentAffinityEventStore {

    /** 클라이언트 MAX_EVENTS와 같은 값 — 링버퍼 길이. */
    static final int MAX_EVENTS = 50;

    private final JdbcTemplate jdbc;

    public ExperimentAffinityEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void createTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS experiment_affinity_event (
                  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                  member_key VARCHAR(40)  NOT NULL,
                  goods_no   BIGINT       NOT NULL,
                  cat3       VARCHAR(7)   NOT NULL,
                  tags       VARCHAR(400) NOT NULL,
                  w          INT          NOT NULL,
                  KEY idx_member (member_key, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    /** 행동 이벤트 1건 INSERT — 서버형이라면 조회·찜·담기마다 일어났을 쓰기다. */
    public void append(String memberKey, AffinityNextStepRequest.Event event) {
        jdbc.update("INSERT INTO experiment_affinity_event (member_key, goods_no, cat3, tags, w) VALUES (?,?,?,?,?)",
                memberKey, event.goodsNo(), event.cat3(), String.join(",", event.tags()), event.w());
    }

    /** 최신 {@value #MAX_EVENTS}건 SELECT — 서버형이라면 화면마다 일어났을 프로필 읽기다. */
    public List<AffinityNextStepRequest.Event> recentEvents(String memberKey) {
        return jdbc.query("""
                        SELECT goods_no, cat3, tags, w FROM experiment_affinity_event
                        WHERE member_key = ? ORDER BY id DESC LIMIT %d
                        """.formatted(MAX_EVENTS),
                (rs, i) -> new AffinityNextStepRequest.Event(
                        rs.getLong("goods_no"), rs.getString("cat3"),
                        Arrays.asList(rs.getString("tags").split(",")), rs.getInt("w")),
                memberKey);
    }
}
