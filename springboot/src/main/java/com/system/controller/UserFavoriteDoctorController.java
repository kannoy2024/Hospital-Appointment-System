package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.UserFavoriteDoctorRequest;
import com.system.dto_vo.vo.UserFavoriteDoctorVO;
import com.system.service.UserFavoriteDoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "医生收藏", description = "患者收藏/取消收藏医生")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class UserFavoriteDoctorController {

    private final UserFavoriteDoctorService userFavoriteDoctorService;

    /**
     * 收藏医生
     * @param userFavoriteDoctorRequest
     * @return
     */
    @PreAuthorize("hasRole('USER')")
    @Log(module = "医生收藏",action = "收藏医生")
    @Operation(summary = "收藏医生")
    @PostMapping
    public Result<UserFavoriteDoctorVO> add(@Valid @RequestBody UserFavoriteDoctorRequest userFavoriteDoctorRequest) {
        return Result.ok(userFavoriteDoctorService.add(userFavoriteDoctorRequest.getDoctorId()));
    }



    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "我的医生收藏列表")
    @GetMapping
    public Result<List<UserFavoriteDoctorVO>> listMine() {
        return Result.ok(userFavoriteDoctorService.listMine());
    }


    @PreAuthorize("hasRole('USER')")
    @Log(module = "医生收藏",action = "取消收藏医生")
    @Operation(summary = "取消收藏")
    @DeleteMapping({"/{doctorId}"})
    public Result<Void> remove(@PathVariable Long doctorId) {
        userFavoriteDoctorService.remove(doctorId);
        return Result.ok();
    }

    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "是否已收藏")
    @GetMapping("/check/{doctorId}")
    public Result<Boolean> check(@PathVariable Long doctorId) {
        return Result.ok(userFavoriteDoctorService.isFavorited(doctorId));
    }
}
