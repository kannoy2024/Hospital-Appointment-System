package com.system.controller;

import com.system.common.Result;
import com.system.dto_vo.dto.TriageAnalyzeRequest;
import com.system.dto_vo.vo.TriageAnalyzeResponse;
import com.system.service.TriageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "智能导诊", description = "AI 症状分析与科室推荐")
@RestController
@RequestMapping("/api/triage")
@AllArgsConstructor
public class TriageController {

    private final TriageService triageService;


    @Operation(summary = "分析症状并推荐科室")
    @PostMapping("/analyze")
    public Result<TriageAnalyzeResponse> analyze(@Valid @RequestBody TriageAnalyzeRequest request) {
        return Result.ok(triageService.analyze(request.getSymptom()));
    }
}
