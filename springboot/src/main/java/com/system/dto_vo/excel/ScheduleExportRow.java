package com.system.dto_vo.excel;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import cn.idev.excel.annotation.write.style.ContentRowHeight;
import cn.idev.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.time.LocalDate;

//排班表导出：一行 = 一个医生的一天，六个时段横向展开
//时段列的值是 "已约/总号源"，例如 "1/5"；该时段没排班则写 "—"
@Data
@HeadRowHeight(22)
@ContentRowHeight(18)
public class ScheduleExportRow {

    //仅用于排序，不写入表格
    @ExcelIgnore
    private Long doctorId;


    //此处导出使用的是fastExcel提供的日期注解
    @ExcelProperty("日期")
    @DateTimeFormat("yyyy-MM-dd")
    @ColumnWidth(12)
    private LocalDate scheduleDate;

    @ExcelProperty("星期")
    @ColumnWidth(6)
    private String weekday;

    @ExcelProperty("科室")
    @ColumnWidth(12)
    private String departmentName;

    @ExcelProperty("医生")
    @ColumnWidth(10)
    private String doctorName;

    @ExcelProperty("08:00")
    @ColumnWidth(8)
    private String slot0800;

    @ExcelProperty("09:00")
    @ColumnWidth(8)
    private String slot0900;

    @ExcelProperty("10:00")
    @ColumnWidth(8)
    private String slot1000;

    @ExcelProperty("14:00")
    @ColumnWidth(8)
    private String slot1400;

    @ExcelProperty("15:00")
    @ColumnWidth(8)
    private String slot1500;

    @ExcelProperty("16:00")
    @ColumnWidth(8)
    private String slot1600;

    @ExcelProperty("当日总号源")
    @ColumnWidth(12)
    private Integer totalQuota;

    @ExcelProperty("当日已约")
    @ColumnWidth(10)
    private Integer bookedCount;

    @ExcelProperty("当日剩余")
    @ColumnWidth(10)
    private Integer remainQuota;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String statusText;
}
