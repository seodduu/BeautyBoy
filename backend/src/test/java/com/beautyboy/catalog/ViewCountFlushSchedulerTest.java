package com.beautyboy.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 플러시 스케줄러. <b>테스트에 Redis를 요구하지 않는다</b> — RedisTemplate/HashOperations를 mock으로 대체해
 * 순수하게 플러시 순서(읽고 → DB 반영 → 비운다)와 실패 격리만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ViewCountFlushSchedulerTest {

    private static final String KEY = "bb:viewcount";

    @Mock
    RedisTemplate<String, Long> redisTemplate;
    @Mock
    HashOperations<String, String, Long> hashOps;
    @Mock
    GoodsRepository goodsRepository;

    ViewCountFlushScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ViewCountFlushScheduler(redisTemplate, goodsRepository);
    }

    @Test
    void 플러시는_버퍼를_읽어_DB에_더하고_버퍼를_비운다() {
        given(redisTemplate.<String, Long>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(KEY)).willReturn(Map.of("1", 5L, "2", 3L));

        scheduler.flush();

        verify(goodsRepository).addViewCount(1L, 5);
        verify(goodsRepository).addViewCount(2L, 3);
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void 버퍼가_비어_있으면_DB를_건드리지_않는다() {
        given(redisTemplate.<String, Long>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(KEY)).willReturn(Map.of());

        scheduler.flush();

        verifyNoInteractions(goodsRepository);
    }

    /**
     * 숫자가 아닌 field 하나가 전체 플러시를 영구히 막으면 안 된다(독약 방지).
     * 건너뛰고 나머지는 반영한 뒤 DEL까지 정상 진행해야 버퍼가 스스로 회복된다.
     */
    @Test
    void 숫자가_아닌_field는_건너뛰고_나머지는_반영한다() {
        given(redisTemplate.<String, Long>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(KEY)).willReturn(Map.of("1", 5L, "깨진필드", 9L));

        assertThatCode(() -> scheduler.flush()).doesNotThrowAnyException();

        verify(goodsRepository).addViewCount(1L, 5);
        verifyNoMoreInteractions(goodsRepository);
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void Redis가_죽어_있으면_로그만_남기고_다음_주기를_기다린다() {
        given(redisTemplate.<String, Long>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(KEY)).willThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> scheduler.flush()).doesNotThrowAnyException();
    }
}
