package com.beautyboy.order;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.member.Member;
import com.beautyboy.member.MemberRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderCreateTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    OrderRepository orderRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 서버가_다시_계산한_금액으로_주문이_생성된다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.payableAmount").value(32000));
    }

    @Test
    void 클라이언트가_보낸_금액은_무시된다() throws Exception {
        // 요청 본문에 금액을 끼워 넣어도 서버 계산이 이긴다.
        // 이 테스트가 깨지면 가격 위변조가 가능하다는 뜻이다.
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("goodsNo", goodsId, "quantity", 1, "unitPrice", 10)),
                "payableAmount", 10,
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payableAmount").value(16000));
    }

    @Test
    void 주문_상품은_이름과_가격이_스냅샷으로_저장된다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("그린티 토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        Order order = orderRepository.findAll().get(0);
        OrderItem item = order.getItems().get(0);
        assertThat(item.getGoodsName()).isEqualTo("그린티 토너");
        assertThat(item.getUnitPrice()).isEqualTo(16000);
        assertThat(item.getLineAmount()).isEqualTo(16000);
    }

    @Test
    void 상품_가격이_바뀌어도_과거_주문서의_금액은_그대로다() throws Exception {
        // 스냅샷의 존재 이유 그 자체. 참조로 뒀다면 여기서 금액이 따라 바뀐다.
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);
        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        entityManager.createNativeQuery("update goods set sale_price = 99000 where id = :id")
                .setParameter("id", goods.getId())
                .executeUpdate();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll().get(0).getPayableAmount()).isEqualTo(16000);
    }

    @Test
    void 초기_상태는_결제대기다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll().get(0).getStatus()).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    void 주문번호는_서로_겹치지_않는다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));
        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll()).extracting(Order::getOrderNo).doesNotHaveDuplicates();
    }

    @Test
    void 없는_상품이_섞이면_404이고_주문이_하나도_남지_않는다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(
                Map.of("goodsNo", goodsId, "quantity", 1),
                Map.of("goodsNo", 999999L, "quantity", 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));

        TestPersistence.DB_왕복_강제(entityManager);

        // 부분 저장되면 결제할 수 없는 반쪽 주문이 남는다.
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void 주문_상품이_비어_있으면_400과_CART_EMPTY() throws Exception {
        Long memberId = 회원_저장();

        주문요청(memberId, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void 재고보다_많이_주문하면_409와_ORDER_OUT_OF_STOCK() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);
        Long optionId = 옵션_저장(goodsId, "50ml", 0, 2);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "optionNo", optionId, "quantity", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_OUT_OF_STOCK"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 주문요청(
            Long memberId, List<Map<String, Object>> items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", items,
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));
        return mockMvc.perform(post("/api/v1/orders")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 회원_저장() {
        Member member = memberRepository.save(
                new Member("buyer" + System.nanoTime() + "@beautyboy.dev", "encoded-password", "민수"));
        return member.getId();
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }

    private Long 옵션_저장(Long goodsId, String name, int addPrice, int stock) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        // GoodsOption 실제 생성자는 sortOrder까지 5개 인자를 받는다(계획서 4개는 실 코드와 다름).
        com.beautyboy.catalog.GoodsOption option =
                new com.beautyboy.catalog.GoodsOption(goods, name, addPrice, stock, 0);
        goods.getOptions().add(option);
        // goods가 이미 영속 상태라 save()는 merge()로 처리되고, transient 자식(option)은
        // cascade-merge로 "복사본"이 persist된다. 원래의 option 참조에는 id가 채워지지 않으므로
        // DB 왕복 후 재조회한 엔티티에서 id를 읽어야 한다.
        goodsRepository.saveAndFlush(goods);
        TestPersistence.DB_왕복_강제(entityManager);
        Goods reloaded = goodsRepository.findById(goodsId).orElseThrow();
        return reloaded.getOptions().stream()
                .filter(o -> o.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
