package com.beautyboy.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ingredient_rule", uniqueConstraints = @UniqueConstraint(columnNames = {"category_a", "category_b"}))
public class IngredientRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_a", nullable = false, length = 40)
    private String categoryA;

    @Column(name = "category_b", nullable = false, length = 40)
    private String categoryB;

    @Column(nullable = false, length = 20)
    private String verdict;

    @Column(nullable = false, length = 300)
    private String reason;

    protected IngredientRule() {
    }

    public IngredientRule(Long id, String categoryA, String categoryB, String verdict, String reason) {
        this.id = id;
        this.categoryA = categoryA;
        this.categoryB = categoryB;
        this.verdict = verdict;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getCategoryA() {
        return categoryA;
    }

    public String getCategoryB() {
        return categoryB;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getReason() {
        return reason;
    }
}
