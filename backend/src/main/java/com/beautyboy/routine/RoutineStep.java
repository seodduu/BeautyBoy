package com.beautyboy.routine;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "routine_step")
public class RoutineStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // V50 스키마 TINYINT. 실 MySQL validate 통과를 위해 int + @JdbcTypeCode(TINYINT).
    @Column(name = "step_order", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int stepOrder;

    @Column(name = "step_name", nullable = false, length = 40)
    private String stepName;

    @Column(name = "beginner_tip", nullable = false, length = 200)
    private String beginnerTip;

    // Set으로 매핑한다: 부모(steps)와 자식(stepGoods)을 한 쿼리로 fetch join할 때
    // List(bag) 둘을 동시에 못 가져오는 MultipleBagFetchException을 피하기 위함. @OrderBy로 정렬 유지.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "step_id", nullable = false)
    @OrderBy("sortOrder asc")
    private Set<RoutineStepGoods> stepGoods = new LinkedHashSet<>();

    protected RoutineStep() {
    }

    public RoutineStep(Long id, int stepOrder, String stepName, String beginnerTip, List<RoutineStepGoods> stepGoods) {
        this.id = id;
        this.stepOrder = stepOrder;
        this.stepName = stepName;
        this.beginnerTip = beginnerTip;
        this.stepGoods = new LinkedHashSet<>(stepGoods);
    }

    public Long getId() {
        return id;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getStepName() {
        return stepName;
    }

    public String getBeginnerTip() {
        return beginnerTip;
    }

    public Set<RoutineStepGoods> getStepGoods() {
        return stepGoods;
    }
}
