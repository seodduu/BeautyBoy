package com.beautyboy.member;

import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberService memberService;

    String tokenA;
    String tokenB;

    @BeforeEach
    void 회원_두명_가입_후_로그인() throws Exception {
        memberService.signup(new SignupRequest("member-a@b.com", "pw123456", "에이", null, null, null));
        memberService.signup(new SignupRequest("member-b@b.com", "pw123456", "비", null, null, null));

        tokenA = login("member-a@b.com", "pw123456");
        tokenB = login("member-b@b.com", "pw123456");
    }

    @Test
    void 토큰_없이_내정보_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_배송지_목록_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내정보를_조회하면_비밀번호_해시_없이_기본정보를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("member-a@b.com"))
                .andExpect(jsonPath("$.data.nickname").value("에이"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void 프로필을_수정하면_내정보_조회에_반영된다() throws Exception {
        String body = objectMapper.writeValueAsString(new ProfileRequestFixture("OILY", List.of("TROUBLE", "PORE"), "20s"));

        mockMvc.perform(put("/api/v1/members/me/profile")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skinType").value("OILY"));

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skinType").value("OILY"))
                .andExpect(jsonPath("$.data.ageBand").value("20s"))
                .andExpect(jsonPath("$.data.concerns[0]").value("TROUBLE"));
    }

    @Test
    void 관심사항_합산길이가_200자를_넘으면_400을_반환한다() throws Exception {
        String longConcern = "A".repeat(250);
        String body = objectMapper.writeValueAsString(new ProfileRequestFixture("OILY", List.of(longConcern), "20s"));

        mockMvc.perform(put("/api/v1/members/me/profile")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 첫_배송지는_isDefault값과_무관하게_기본배송지가_된다() throws Exception {
        String body = objectMapper.writeValueAsString(addressFixture("서울", false));

        mockMvc.perform(post("/api/v1/members/me/addresses")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void 새_기본배송지를_등록하면_기존_기본배송지는_해제된다() throws Exception {
        Long firstId = createAddress(tokenA, "첫집", true);
        Long secondId = createAddress(tokenA, "둘째집", true);

        mockMvc.perform(get("/api/v1/members/me/addresses").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 첫 배송지는 더 이상 기본이 아니어야 한다
        String body = objectMapper.writeValueAsString(addressFixture("첫집갱신", false));
        mockMvc.perform(put("/api/v1/members/me/addresses/" + firstId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false));

        MvcResult listResult = mockMvc.perform(get("/api/v1/members/me/addresses").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data");
        boolean secondIsDefault = false;
        boolean firstIsDefault = true;
        for (JsonNode node : list) {
            if (node.get("id").asLong() == secondId) {
                secondIsDefault = node.get("isDefault").asBoolean();
            }
            if (node.get("id").asLong() == firstId) {
                firstIsDefault = node.get("isDefault").asBoolean();
            }
        }
        assertThat(secondIsDefault).isTrue();
        assertThat(firstIsDefault).isFalse();
    }

    @Test
    void 타인의_배송지를_삭제하려하면_FORBIDDEN을_반환한다() throws Exception {
        Long addressId = createAddress(tokenA, "에이집", true);

        mockMvc.perform(delete("/api/v1/members/me/addresses/" + addressId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 존재하지_않는_배송지를_삭제하려하면_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/addresses/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 본인의_배송지는_정상적으로_삭제된다() throws Exception {
        Long addressId = createAddress(tokenA, "에이집", true);

        mockMvc.perform(delete("/api/v1/members/me/addresses/" + addressId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/members/me/addresses").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private Long createAddress(String token, String receiver, boolean isDefault) throws Exception {
        String body = objectMapper.writeValueAsString(addressFixture(receiver, isDefault));
        MvcResult result = mockMvc.perform(post("/api/v1/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture(email, password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asText();
    }

    private AddressRequestFixture addressFixture(String receiver, boolean isDefault) {
        return new AddressRequestFixture(receiver, "010-1234-5678", "12345", "서울시 강남구", "101동 101호", null, null, isDefault);
    }

    private record LoginRequestFixture(String email, String password) {
    }

    private record ProfileRequestFixture(String skinType, List<String> concerns, String ageBand) {
    }

    private record AddressRequestFixture(String receiver, String phone, String zipcode, String address1,
                                          String address2, Object latitude, Object longitude, boolean isDefault) {
    }
}
