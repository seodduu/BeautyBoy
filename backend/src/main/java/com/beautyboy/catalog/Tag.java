package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Tag() {
    }

    public Tag(String name, String kind, String slug, int sortOrder) {
        this.name = name;
        this.kind = kind;
        this.slug = slug;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getSlug() {
        return slug;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
