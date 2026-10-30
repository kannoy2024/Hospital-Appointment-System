package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class LogBatchDeleteRequest {

    @NotNull(message = "日志类型不能为空")
    @Pattern(regexp = "^(login|operation)$", message = "日志类型须为 login 或 operation")
    private String type;

    @NotEmpty(message = "请选择要删除的日志")
    private List<Long> ids;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
