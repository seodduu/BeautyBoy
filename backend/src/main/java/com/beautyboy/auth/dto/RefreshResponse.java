package com.beautyboy.auth.dto;

import com.beautyboy.member.dto.MemberMeResponse;

/**
 * POST /auth/refresh 응답. 로그인 직후엔 accessToken만 내려도 프론트가 별도로
 * GET /members/me를 불러 회원 정보를 채우지만(로그인 흐름은 그대로 둔다 — 이 태스크
 * 범위 밖), 새로고침 부트스트랩은 그 여유가 없다 — App.tsx가 이 응답 하나로
 * accessToken과 member를 동시에 authStore에 채워야 admin 라우팅 가드
 * (RequireAdmin: member?.role !== 'ADMIN')가 새로고침 직후에도 올바르게 판정한다.
 *
 * <p>{@code member}는 {@link MemberMeResponse}를 그대로 재사용한다 — GET /members/me와
 * 같은 필드(id/email/nickname/grade/skinType/concerns/ageBand/role)를 그대로 내려
 * 프론트가 두 응답을 같은 MemberInfo 타입으로 파싱할 수 있게 한다(실제로 프론트
 * MemberInfo가 이 형태를 기대한다 — stores/authStore.ts).
 */
public record RefreshResponse(String accessToken, MemberMeResponse member) {
}
