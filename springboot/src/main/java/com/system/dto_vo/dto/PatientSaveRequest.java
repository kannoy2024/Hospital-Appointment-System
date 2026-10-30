package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "就诊人保存请求")
public class PatientSaveRequest {

    private Long id;

    @Schema(description = "就诊人姓名")
    @NotBlank(message = "就诊人姓名不能为空")
    @Size(min = 1, max = 50, message = "姓名长度为1到50个字符")
    private String name;


    @Schema(description = "联系电话")
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "电话长度不能超过20个字符")
    private String phone;


    @Schema(description = "身份证号")
    @Size(max = 32, message = "身份证号长度不能超过32个字符")
    private String idCard;

    @Schema(description = "性别 1男 2女 0未知")
    private Integer gender;

    @Schema(description = "出生时间")
    private LocalDate birthDate;

    @Schema(description = "民族编号")
    private Integer nation;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "婚姻状态 1已婚 2未婚")
    @NotNull(message = "婚姻状态不能为空")
    private Integer marital;

    @Schema(description = "是否设为默认就诊人 1是 0否")
    private Integer isDefault;


}
