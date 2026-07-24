package com.beautyboy.compat;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.compat.dto.CompatCheckRequest;
import com.beautyboy.compat.dto.CompatCheckResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompatController {

    private final CompatService compatService;

    public CompatController(CompatService compatService) {
        this.compatService = compatService;
    }

    @PostMapping("/api/v1/compat/check")
    public ResponseEntity<ApiResponse<CompatCheckResponse>> check(@RequestBody CompatCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(compatService.check(request)));
    }
}
