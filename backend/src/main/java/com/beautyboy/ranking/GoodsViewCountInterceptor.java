package com.beautyboy.ranking;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상품 상세 조회를 세는 인터셉터.
 *
 * <p>catalog의 서비스에 카운트 코드를 넣지 않은 이유가 둘이다.
 * (1) 경계: 조회수 집계는 랭킹의 관심사다. catalog가 ranking의 존재를 알 이유가 없다.
 * (2) 병렬: 이번 웨이브에서 catalog 파일은 T2(주문) 소유라 T1이 열면 충돌한다.
 *
 * <p>{@code afterCompletion}에서 세는 이유: 응답 상태를 봐야 한다.
 * 404(없는 상품)에 통계를 남기면 존재하지 않는 goods_id로 원장이 오염된다.
 *
 * <p>봇·새로고침 어뷰징은 걸러내지 않는다. MVP 범위이고, 랭킹 점수에서 조회는 가중치 1로
 * 판매(3)·찜(2)보다 낮게 잡혀 있어 단독으로 순위를 뒤집기 어렵다.
 *
 * <p>생성자가 {@code GoodsDailyStatRepository}를 즉시 주입받지 않고 {@code ObjectProvider}로
 * 지연 조회하는 이유: {@code HandlerInterceptor}는 {@code @WebMvcTest} 슬라이스의 기본 스캔
 * 대상이라, JPA 리포지토리가 로딩되지 않는 슬라이스 컨텍스트(예: MemberControllerTest)에도 이
 * 빈이 끼어든다. 즉시 주입이면 그 슬라이스에서 생성자 의존성을 못 찾아 컨텍스트 자체가 뜨지
 * 못한다. 지연 조회로 바꾸면 빈 생성은 항상 성공하고, 저장소가 실제로 필요한 시점(상세 조회
 * 응답 완료)은 그런 슬라이스에서 애초에 발생하지 않는다.
 */
@Component
public class GoodsViewCountInterceptor implements HandlerInterceptor {

    /** /api/v1/goods/{숫자} 만 센다. /description, /recommended, /ingredients는 상세 조회가 아니다. */
    private static final Pattern GOODS_DETAIL = Pattern.compile("^/api/v1/goods/(\\d+)$");

    private final ObjectProvider<GoodsDailyStatRepository> goodsDailyStatRepositoryProvider;

    public GoodsViewCountInterceptor(ObjectProvider<GoodsDailyStatRepository> goodsDailyStatRepositoryProvider) {
        this.goodsDailyStatRepositoryProvider = goodsDailyStatRepositoryProvider;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!HttpStatus.valueOf(response.getStatus()).is2xxSuccessful()) {
            return;
        }

        Matcher matcher = GOODS_DETAIL.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            return;
        }

        goodsDailyStatRepositoryProvider.getObject().upsertViewCount(
                Long.valueOf(matcher.group(1)), LocalDate.now(), 1);
    }
}
