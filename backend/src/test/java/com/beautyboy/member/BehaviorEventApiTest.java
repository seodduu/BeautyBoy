package com.beautyboy.member;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 행동 로그 수집 API 통합 테스트. 레이트 리밋 한도는 프로퍼티로 3까지 낮춰(기본 120으로는
 * 테스트가 429를 재현하려면 요청을 121번 보내야 한다) 4번째 요청에서 429를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "beautyboy.behavior-event.limit-per-minute=3")
@Transactional
class BehaviorEventApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    BrandRepository brandRepository;

    @Autowired
    GoodsRepository goodsRepository;

    @Autowired
    BehaviorEventRepository behaviorEventRepository;

    Long goodsId;

    @BeforeEach
    void 상품_준비() {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        goodsId = goodsRepository.save(
                new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 16000, 16000)).getId();
    }

    @Test
    void 동의한_회원의_이벤트는_저장된다() throws Exception {
        String token = 동의한_회원_토큰();

        이벤트요청(token, goodsId, "VIEW")
                .andExpect(status().isNoContent());

        assertThat(behaviorEventRepository.countByMemberId(회원조회("consent-buy@b.com"))).isEqualTo(1);
    }

    @Test
    void 동의하지_않은_회원의_이벤트는_저장되지_않는다() throws Exception {
        String token = 회원가입_후_로그인("noconsent@b.com");

        이벤트요청(token, goodsId, "VIEW")
                .andExpect(status().isNoContent());

        assertThat(behaviorEventRepository.countByMemberId(회원조회("noconsent@b.com"))).isEqualTo(0);
    }

    @Test
    void 없는_상품은_조용히_버린다() throws Exception {
        String token = 동의한_회원_토큰();

        이벤트요청(token, 999999999L, "VIEW")
                .andExpect(status().isNoContent());

        assertThat(behaviorEventRepository.countByMemberId(회원조회("consent-buy@b.com"))).isEqualTo(0);
    }

    @Test
    void kind가_이상하면_400이다() throws Exception {
        String token = 동의한_회원_토큰();

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("goodsNo", goodsId, "kind", "HOVER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void goodsNo가_없으면_400이다() throws Exception {
        String token = 동의한_회원_토큰();

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("kind", "VIEW"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("goodsNo", goodsId, "kind", "VIEW"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 한도를_넘으면_429다() throws Exception {
        String token = 동의한_회원_토큰();

        for (int i = 0; i < 3; i++) {
            이벤트요청(token, goodsId, "VIEW").andExpect(status().isNoContent());
        }

        이벤트요청(token, goodsId, "VIEW")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 저장된_이벤트는_네_칸이_맞다() throws Exception {
        String token = 동의한_회원_토큰();

        이벤트요청(token, goodsId, "CART").andExpect(status().isNoContent());

        Long memberId = 회원조회("consent-buy@b.com");
        List<BehaviorEvent> events = behaviorEventRepository.findAll().stream()
                .filter(event -> event.getMemberId().equals(memberId))
                .toList();
        assertThat(events).hasSize(1);
        BehaviorEvent event = events.get(0);
        assertThat(event.getMemberId()).isEqualTo(memberId);
        assertThat(event.getGoodsId()).isEqualTo(goodsId);
        assertThat(event.getKind()).isEqualTo(BehaviorKind.CART);
        assertThat(event.getCreatedAt()).isNotNull();
    }

    private org.springframework.test.web.servlet.ResultActions 이벤트요청(String token, Long goodsNo, String kind)
            throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("goodsNo", goodsNo, "kind", kind))));
    }

    private String 동의한_회원_토큰() throws Exception {
        String token = 회원가입_후_로그인("consent-buy@b.com");
        memberService.updateBehaviorConsent(회원조회("consent-buy@b.com"), true);
        return token;
    }

    private String 회원가입_후_로그인(String email) throws Exception {
        memberService.signup(new SignupRequest(email, "pw123456", "행동로그맨", null, null, null));
        return 로그인(email, "pw123456");
    }

    private Long 회원조회(String email) {
        return memberRepository.findByEmail(email).orElseThrow().getId();
    }

    private String 로그인(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture(email, password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asText();
    }

    private record LoginRequestFixture(String email, String password) {
    }
}
