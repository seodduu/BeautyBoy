package com.beautyboy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 카테고리는 계층을 FK 연관관계가 아니라 코드 접두사로 인코딩한다
 * (C001 → C001001 → C001001001). parentCode를 String 스칼라로만 두는 이유:
 * 트리 조립을 CategoryService가 메모리에서 하므로 FK 객체 그래프가 필요 없고,
 * 연관관계로 두면 LAZY 프록시로 인한 N+1을 원천적으로 유발하게 된다.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @Column(length = 12)
    private String code;

    @Column(name = "parent_code", length = 12)
    private String parentCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int depth;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
    }

    public Category(String code, String parentCode, String name, int depth, int sortOrder) {
        this.code = code;
        this.parentCode = parentCode;
        this.name = name;
        this.depth = depth;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getParentCode() {
        return parentCode;
    }

    public String getName() {
        return name;
    }

    public int getDepth() {
        return depth;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
