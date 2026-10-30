package com.system.dto_vo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


//统计图视图
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartItemVO {

    private String name;
    private long value;
}
