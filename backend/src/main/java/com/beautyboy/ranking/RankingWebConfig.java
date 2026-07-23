package com.beautyboy.ranking;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ranking이 자기 인터셉터를 스스로 등록한다.
 *
 * <p>공용 WebMvc 설정 파일을 만들지 않는 이유: 그런 파일이 생기는 순간 다음 웨이브의 여러 터미널이
 * 같은 파일에 인터셉터를 추가하려 들어 충돌 지점이 된다. 도메인이 자기 설정을 들고 있으면 그럴 일이 없다.
 */
@Configuration
public class RankingWebConfig implements WebMvcConfigurer {

    private final GoodsViewCountInterceptor goodsViewCountInterceptor;

    public RankingWebConfig(GoodsViewCountInterceptor goodsViewCountInterceptor) {
        this.goodsViewCountInterceptor = goodsViewCountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(goodsViewCountInterceptor).addPathPatterns("/api/v1/goods/*");
    }
}
