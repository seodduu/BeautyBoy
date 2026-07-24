package com.beautyboy.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    // V62에서 추가한 판정 엔진 조인 키(nullable). reg_flag.inci_name과 매칭한다.
    @Column(name = "inci_name", length = 255)
    private String inciName;

    @Column(nullable = false, length = 40)
    private String category;

    // V11 스키마가 두 컬럼을 TINYINT로 정의한다(스키마가 진실). 실 MySQL의
    // ddl-auto=validate가 int(INTEGER)와 TINYINT를 불일치로 보므로 JDBC 타입을 맞춘다.
    @Column(name = "irritation_level", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int irritationLevel;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
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

    public String getInciName() {
        return inciName;
    }

    /** 백필은 V62(Flyway)가 하지만, H2 슬라이스 테스트는 픽스처로 직접 세팅한다. */
    public void setInciName(String inciName) {
        this.inciName = inciName;
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
