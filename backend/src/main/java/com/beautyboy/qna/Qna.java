package com.beautyboy.qna;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 상품 Q&A 질문.
 *
 * <p>{@code answer}·{@code answeredAt}은 V41 스키마에 미리 두되(스키마가 진실),
 * 답변 등록 API는 만들지 않는다 — 관리자 답변은 Wave 4 admin 몫이다.
 * {@code memberId}/{@code goodsId}가 스칼라인 이유: member·catalog는 타 도메인이라
 * 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계).
 */
@Entity
@Table(name = "qna")
public class Qna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "question", nullable = false, length = 1000)
    private String question;

    @Column(name = "answer", length = 2000)
    private String answer;

    @Column(name = "is_secret", nullable = false)
    private boolean isSecret;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "WAITING";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    protected Qna() {
    }

    public Qna(Long memberId, Long goodsId, String question, boolean isSecret) {
        this.memberId = memberId;
        this.goodsId = goodsId;
        this.question = question;
        this.isSecret = isSecret;
        this.status = "WAITING";
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public boolean isSecret() {
        return isSecret;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    /** 관리자 답변 등록. 이미 답변된 문의인지(QNA_ALREADY_ANSWERED)는 호출자(서비스)가 먼저 판정한다. */
    public void answer(String answer) {
        this.answer = answer;
        this.status = "ANSWERED";
        this.answeredAt = LocalDateTime.now();
    }
}
