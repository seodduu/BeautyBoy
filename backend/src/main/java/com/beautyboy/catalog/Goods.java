package com.beautyboy.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 목록 조회 경로(`GoodsQueryRepository.findList`)는 이 엔티티를 로딩하지 않는다.
 * JPA에서 엔티티를 select하면(예: {@code select g from Goods g}) select 절에
 * 컬럼을 안 적어도 매핑된 컬럼이 전부 — description(TEXT) 포함 — 함께 로딩된다.
 * SQL 프로젝션이 아니라 엔티티 로딩이기 때문이다. 그래서 목록 쿼리는 이 엔티티를
 * 거치지 않고 처음부터 {@code Object[]} 스칼라 프로젝션으로 필요한 컬럼만 뽑아
 * {@code GoodsQueryRepository.GoodsRow}에 옮겨 담는다(description 미포함).
 * 이 엔티티는 단건 상세 조회(Task 1-5)와 저장/수정 경로에서만 로딩된다.
 */
@Entity
@Table(name = "goods")
public class Goods {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_SOLD_OUT = "SOLD_OUT";
    public static final String STATUS_HIDDEN = "HIDDEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "category_code", nullable = false, length = 12)
    private String categoryCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", nullable = false, length = 300)
    private String thumbnailUrl;

    @Column(name = "list_price", nullable = false)
    private int listPrice;

    @Column(name = "sale_price", nullable = false)
    private int salePrice;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ON_SALE;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "sales_count", nullable = false)
    private int salesCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsOption> options = new ArrayList<>();

    protected Goods() {
    }

    public Goods(Brand brand, String categoryCode, String name, String summary, String thumbnailUrl,
                 int listPrice, int salePrice) {
        this.brand = brand;
        this.categoryCode = categoryCode;
        this.name = name;
        this.summary = summary;
        this.thumbnailUrl = thumbnailUrl;
        this.listPrice = listPrice;
        this.salePrice = salePrice;
    }

    public void increaseViewCount(int amount) {
        this.viewCount += amount;
    }

    public void increaseSalesCount(int amount) {
        this.salesCount += amount;
    }

    public void hide() {
        this.status = STATUS_HIDDEN;
    }

    /** 관리자 수정 — 이름·요약·가격·상태·카테고리·썸네일을 통째로 덮어쓴다. */
    public void updateInfo(String categoryCode, String name, String summary, String thumbnailUrl,
                            int listPrice, int salePrice, String status) {
        this.categoryCode = categoryCode;
        this.name = name;
        this.summary = summary;
        this.thumbnailUrl = thumbnailUrl;
        this.listPrice = listPrice;
        this.salePrice = salePrice;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Brand getBrand() {
        return brand;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public int getListPrice() {
        return listPrice;
    }

    public int getSalePrice() {
        return salePrice;
    }

    public String getStatus() {
        return status;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<GoodsOption> getOptions() {
        return options;
    }
}
