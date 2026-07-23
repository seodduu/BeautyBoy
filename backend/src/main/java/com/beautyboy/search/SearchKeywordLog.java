package com.beautyboy.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 검색어 1건의 기록. 인기검색어 집계의 원장이다(설계 8장).
 *
 * <p>{@code memberId}를 FK 없는 스칼라로 들고 있는 이유: member는 타 도메인이라
 * 엔티티를 직접 참조할 수 없고(패키지 = 서비스 경계), 로그는 회원이 탈퇴해도 통계로 남아야 한다.
 * 비로그인 검색도 기록하므로 null을 허용한다.
 */
@Entity
@Table(name = "search_keyword_log")
public class SearchKeywordLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    protected SearchKeywordLog() {
    }

    public SearchKeywordLog(String keyword, Long memberId, LocalDateTime searchedAt) {
        this.keyword = keyword;
        this.memberId = memberId;
        this.searchedAt = searchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}
