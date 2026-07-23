package com.beautyboy.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "member_profile")
public class MemberProfile {

    @Id
    private Long memberId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "skin_type", length = 20)
    private String skinType;

    @Column(name = "concerns", length = 200)
    private String concerns;

    @Column(name = "age_band", length = 10)
    private String ageBand;

    protected MemberProfile() {
    }

    public MemberProfile(String skinType, List<String> concerns, String ageBand) {
        this.skinType = skinType;
        this.concerns = toConcernsColumn(concerns);
        this.ageBand = ageBand;
    }

    void assignMember(Member member) {
        this.member = member;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Member getMember() {
        return member;
    }

    public String getSkinType() {
        return skinType;
    }

    public List<String> getConcerns() {
        if (concerns == null || concerns.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(concerns.split(","));
    }

    public String getAgeBand() {
        return ageBand;
    }

    private static String toConcernsColumn(List<String> concerns) {
        if (concerns == null || concerns.isEmpty()) {
            return null;
        }
        return concerns.stream().collect(Collectors.joining(","));
    }
}
