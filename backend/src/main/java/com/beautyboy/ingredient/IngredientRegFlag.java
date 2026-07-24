package com.beautyboy.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 성분 규제/주의 플래그 조회 사전(V60/V61). INCI 키로 "어떤 성분이 어느 공식 목록에 있는가"를 담는다.
 * 운영에서는 Flyway 시드(V61)가 채우고 애플리케이션은 읽기만 한다. H2 슬라이스 테스트는 생성자로 직접 심는다.
 */
@Entity
@Table(name = "ingredient_reg_flag")
public class IngredientRegFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inci_name", nullable = false, length = 255)
    private String inciName;

    @Column(name = "kr_name", length = 255)
    private String krName;

    @Column(name = "cas_no", length = 200)
    private String casNo;

    @Column(name = "flag_type", nullable = false, length = 20)
    private String flagType;

    @Column(name = "source", nullable = false, length = 60)
    private String source;

    @Column(name = "source_ref", columnDefinition = "TEXT")
    private String sourceRef;

    protected IngredientRegFlag() {
    }

    public IngredientRegFlag(String inciName, String krName, String casNo,
                             String flagType, String source, String sourceRef) {
        this.inciName = inciName;
        this.krName = krName;
        this.casNo = casNo;
        this.flagType = flagType;
        this.source = source;
        this.sourceRef = sourceRef;
    }

    public Long getId() {
        return id;
    }

    public String getInciName() {
        return inciName;
    }

    public String getKrName() {
        return krName;
    }

    public String getCasNo() {
        return casNo;
    }

    public String getFlagType() {
        return flagType;
    }

    public String getSource() {
        return source;
    }

    public String getSourceRef() {
        return sourceRef;
    }
}
