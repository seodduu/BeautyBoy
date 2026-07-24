package com.beautyboy.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoutineTemplateRepository extends JpaRepository<RoutineTemplate, Long> {

    /**
     * 템플릿 + 단계 + 단계별추천을 한 쿼리로 fetch join(N+1 방지).
     * template→steps→stepGoods는 한 경로의 중첩 컬렉션이라 MultipleBagFetchException이 나지 않는다.
     */
    @Query("""
            select distinct t from RoutineTemplate t
            left join fetch t.steps s
            left join fetch s.stepGoods
            where t.skinType = :skinType and t.timeSlot = :timeSlot
            """)
    Optional<RoutineTemplate> findGraphBySkinTypeAndTimeSlot(@Param("skinType") String skinType,
                                                             @Param("timeSlot") String timeSlot);
}
