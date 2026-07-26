package com.beautyboy.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 루틴 전이 규칙 — 이 상품(카테고리·태그)을 담았을 때 다음에 무엇을 추천할지 정하는 규칙 한 줄.
 * reason이 화면 문구의 유일한 출처. 연관관계 없이 스칼라만 매핑한다(타 도메인 참조는 서비스 경유).
 */
@Entity
@Table(name = "routine_flow_rule")
public class RoutineFlowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_category_code", nullable = false, length = 12)
    private String fromCategoryCode;

    @Column(name = "from_tag_slug", length = 40)
    private String fromTagSlug;

    @Column(name = "to_category_code", nullable = false, length = 12)
    private String toCategoryCode;

    @Column(name = "to_tag_slug", length = 40)
    private String toTagSlug;

    @Column(name = "edge_kind", nullable = false, length = 20)
    private String edgeKind;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "priority", nullable = false)
    private int priority;

    protected RoutineFlowRule() {
    }

    public RoutineFlowRule(Long id, String fromCategoryCode, String fromTagSlug, String toCategoryCode,
                            String toTagSlug, String edgeKind, String reason, int priority) {
        this.id = id;
        this.fromCategoryCode = fromCategoryCode;
        this.fromTagSlug = fromTagSlug;
        this.toCategoryCode = toCategoryCode;
        this.toTagSlug = toTagSlug;
        this.edgeKind = edgeKind;
        this.reason = reason;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public String getFromCategoryCode() {
        return fromCategoryCode;
    }

    public String getFromTagSlug() {
        return fromTagSlug;
    }

    public String getToCategoryCode() {
        return toCategoryCode;
    }

    public String getToTagSlug() {
        return toTagSlug;
    }

    public String getEdgeKind() {
        return edgeKind;
    }

    public String getReason() {
        return reason;
    }

    public int getPriority() {
        return priority;
    }
}
