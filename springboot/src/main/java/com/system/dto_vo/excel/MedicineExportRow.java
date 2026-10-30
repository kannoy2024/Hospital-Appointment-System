package com.system.dto_vo.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import cn.idev.excel.annotation.format.NumberFormat;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//金额一律用xxx yuan来命名。使用“元”
//药品管理导出表
@Data
public class MedicineExportRow {

    @ExcelProperty("药品编号")
    @ColumnWidth(12)
    private Long id;


    @ExcelProperty("药品名称")
    @ColumnWidth(16)
    private String name;


    @ExcelProperty("药品规格")
    @ColumnWidth(16)
    private String spec;

    @ExcelProperty("药品单位")
    @ColumnWidth(16)
    private String unit;

    @ExcelProperty("药品单价（元）")
    @NumberFormat("#,##0.00")
    @ColumnWidth(16)
    private BigDecimal priceYuan;

    @ExcelProperty("库存数量")
    @ColumnWidth(16)
    private Integer stock;

    @ExcelProperty("用途说明")
    @ColumnWidth(16)
    private String usageDesc;

    @ExcelProperty("状态")
    @ColumnWidth(16)
    private String statusText;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(16)
    private LocalDateTime createTime;
}
