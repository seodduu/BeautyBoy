package com.beautyboy.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "irritation_level", nullable = false)
    private int irritationLevel;

    @Column(nullable = false)
    private int comedogenic;

    @Column(nullable = false, length = 200)
    private String summary;

    protected Ingredient() {
    }

    public Ingredient(String name, String category, int irritationLevel, int comedogenic, String summary) {
        this.name = name;
        this.category = category;
        this.irritationLevel = irritationLevel;
        this.comedogenic = comedogenic;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getIrritationLevel() {
        return irritationLevel;
    }

    public int getComedogenic() {
        return comedogenic;
    }

    public String getSummary() {
        return summary;
    }
}
