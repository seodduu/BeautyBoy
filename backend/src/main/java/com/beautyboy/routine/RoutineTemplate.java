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
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "routine_template",
        uniqueConstraints = @UniqueConstraint(columnNames = {"skin_type", "time_slot"}))
public class RoutineTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "skin_type", nullable = false, length = 20)
    private String skinType;

    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    @Column(nullable = false, length = 300)
    private String description;

    // Set 매핑 이유는 RoutineStep.stepGoods와 동일(중첩 fetch join 시 MultipleBagFetchException 회피).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "template_id", nullable = false)
    @OrderBy("stepOrder asc")
    private Set<RoutineStep> steps = new LinkedHashSet<>();

    protected RoutineTemplate() {
    }

    public RoutineTemplate(Long id, String name, String skinType, String timeSlot, String description,
                           List<RoutineStep> steps) {
        this.id = id;
        this.name = name;
        this.skinType = skinType;
        this.timeSlot = timeSlot;
        this.description = description;
        this.steps = new LinkedHashSet<>(steps);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSkinType() {
        return skinType;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getDescription() {
        return description;
    }

    public Set<RoutineStep> getSteps() {
        return steps;
    }
}
