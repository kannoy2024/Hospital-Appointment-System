package com.system.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体，与前端 request.js 约定：code=200 为成功，data 为业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    @Schema(description = "返回的代号")
    private int code;

    @Schema(description = "简短的描述")
    private String message;

    @Schema(description = "数据体")
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

}
