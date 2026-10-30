package com.system.dto_vo.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import cn.idev.excel.annotation.format.NumberFormat;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


//金额一律用xxx yuan来命名。使用“元”
//用户预约记录导出表
@Data
public class AppointmentExportRow {

    @ExcelProperty("预约编号")
    @ColumnWidth(12)
    private Long id;

    @ExcelProperty("就诊日期")
    @DateTimeFormat("yyyy-MM-dd")
    @ColumnWidth(12)
    private LocalDate scheduleDate;

    @ExcelProperty("时段")
    @ColumnWidth(10)
    private String timeSlot;

    @ExcelProperty("科室")
    @ColumnWidth(12)
    private String departmentName;

    @ExcelProperty("医生")
    @ColumnWidth(10)
    private String doctorName;

    @ExcelProperty("职称")
    @ColumnWidth(10)
    private String doctorTitle;

    @ExcelProperty("就诊人")
    @ColumnWidth(10)
    private String patientName;

    @ExcelProperty("手机号")
    @ColumnWidth(14)
    private String patientPhone;

    @ExcelProperty("用户昵称")
    @ColumnWidth(14)
    private String userNickname;

    @ExcelProperty("挂号费(元)")
    @NumberFormat("#,##0.00")
    @ColumnWidth(12)
    private BigDecimal amountYuan;

    @ExcelProperty("支付方式")
    @ColumnWidth(12)
    private String payMethodText;

    @ExcelProperty("支付时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(20)
    private LocalDateTime payTime;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String statusText;

    @ExcelProperty("处方状态")
    @ColumnWidth(12)
    private String prescriptionStatusText;

    @ExcelProperty("处方金额(元)")
    @NumberFormat("#,##0.00")
    @ColumnWidth(14)
    private BigDecimal prescriptionAmountYuan;

    @ExcelProperty("已评价")
    @ColumnWidth(8)
    private String evaluatedText;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(20)
    private LocalDateTime createTime;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}