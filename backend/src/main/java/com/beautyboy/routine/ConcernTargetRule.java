package com.beautyboy.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 고민 → 목표 단계 규칙 한 줄(설계 §5.1). 프로필만 있고 앵커 상품이 없는 티어1에서
 * {@link RoutineFlowRule}의 from이 성립하지 않아 따로 둔 테이블이다.
 * reason이 화면 문구의 유일한 출처. 연관관계 없이 스칼라만 매핑한다(타 도메인 참조는 서비스 경유).
 *
 * <p>시드 전용이고 관리자 CRUD가 없다 — 애플리케이션은 읽기로만 쓴다.
 */
@Entity
@Table(name = "concern_target_rule")
public class ConcernTargetRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concern_tag_slug", nullable = false, length = 40)
    private String concernTagSlug;

    @Column(name = "to_category_code", nullable = false, length = 12)
    private String toCategoryCode;

    @Column(name = "to_tag_slug", nullable = false, length = 40)
    private String toTagSlug;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "priority", nullable = false)
    private int priority;

    protected ConcernTargetRule() {
    }

    public ConcernTargetRule(Long id, String concernTagSlug, String toCategoryCode, String toTagSlug,
                             String reason, int priority) {
        this.id = id;
        this.concernTagSlug = concernTagSlug;
        this.toCategoryCode = toCategoryCode;
        this.toTagSlug = toTagSlug;
        this.reason = reason;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public String getConcernTagSlug() {
        return concernTagSlug;
    }

    public String getToCategoryCode() {
        return toCategoryCode;
    }

    public String getToTagSlug() {
        return toTagSlug;
    }

    public String getReason() {
        return reason;
    }

    public int getPriority() {
        return priority;
    }
}
