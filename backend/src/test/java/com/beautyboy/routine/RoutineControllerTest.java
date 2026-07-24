package com.beautyboy.routine;

import com.beautyboy.routine.dto.RoutineResponse;
import com.beautyboy.routine.dto.RoutineStepResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoutineControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    RoutineQueryService routineQueryService;

    private static RoutineResponse 다섯단계_응답() {
        List<RoutineStepResponse> steps = new ArrayList<>();
        for (int order = 1; order <= 5; order++) {
            steps.add(new RoutineStepResponse(order, "단계" + order, "팁" + order, List.of()));
        }
        return new RoutineResponse(1L, "건성 루틴", "DRY", "BASIC", "설명", steps);
    }

    @Test
    void 인증없이_루틴을_조회하면_200과_5단계를_반환한다() throws Exception {
        given(routineQueryService.find(any(), any())).willReturn(다섯단계_응답());

        mockMvc.perform(get("/api/v1/routines").param("skinType", "DRY").param("time", "BASIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.templateId").value(1))
                .andExpect(jsonPath("$.data.skinType").value("DRY"))
                .andExpect(jsonPath("$.data.steps.length()").value(5))
                .andExpect(jsonPath("$.data.steps[0].stepOrder").value(1));
    }
}
