package com.system.service.impl;

import com.system.common.BusinessException;
import com.system.common.ExcelExporter;
import com.system.common.PageResult;
import com.system.dto_vo.excel.ScheduleExportRow;
import com.system.dto_vo.vo.ScheduleVO;
import com.system.service.ScheduleExportService;
import com.system.service.ScheduleService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleExportServiceImpl implements ScheduleExportService {
    //先定义常量内容
    private static final String FILE_BASE_NAME = "排班表";
    private static final String SHEET_NAME = "时段明细";

    private static final List<String> SLOT_ORDER =
            List.of("08:00", "09:00", "10:00", "14:00", "15:00", "16:00");

    private static final String[] WEEKDAY_CN =
            {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private static final String DASH = "—";

    private final ScheduleService scheduleService;

    @Value("${app.export.schedule-max-rows:20000}")
    private int maxRows;

    @Value("${app.export.schedule-max-days:31}")
    private int maxDays;


    //私有方法

    //时间日期判断
    //1 开始结束都不传，范围为当前月份一个月
    //2 只传开始，不传结束，范围为开始时间到这个月月底
    //3 只传结束，不传开始，范围为这个月开始到结束时间
    private static LocalDate[] normaliseRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{
                    today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())
            };
        }
        // 只传开始 → 开始日期所在月的月末
        if (end == null) {
            return new LocalDate[]{
                    start,
                    start.withDayOfMonth(start.lengthOfMonth())
            };
        }
        // 只传结束 → 结束日期所在月的月初
        if (start == null) {
            return new LocalDate[]{
                    end.withDayOfMonth(1),
                    end
            };
        }
        return new LocalDate[]{start, end};
    }

    private static String orDash(String value) {
        return value == null ? DASH : value;
    }

    /**
     * 一组「同一医生-同一天」的排班 → 一行
     */
    private ScheduleExportRow toRow(List<ScheduleVO> group) {
        ScheduleVO head = group.getFirst();
        ScheduleExportRow row = new ScheduleExportRow();
        row.setDoctorId(head.getDoctorId());
        row.setScheduleDate(head.getScheduleDate());
        row.setWeekday(WEEKDAY_CN[head.getScheduleDate().getDayOfWeek().getValue() - 1]);
        row.setDepartmentName(head.getDepartmentName());
        row.setDoctorName(head.getDoctorName());

        String[] cells = new String[SLOT_ORDER.size()];
        int total = 0;
        int booked = 0;
        int normalSlots = 0;
        int stoppedSlots = 0;

        for (ScheduleVO slot : group) {
            int idx = SLOT_ORDER.indexOf(slot.getTimeSlot());
            if (idx < 0) {
                // 时段是业务常量；出现未知值说明数据有问题，不能静默丢弃
                log.warn("[Export] 未知时段 {}，scheduleId={}，已跳过", slot.getTimeSlot(), slot.getId());
                continue;
            }
            int slotTotal = slot.getTotalQuota() == null ? 0 : slot.getTotalQuota();
            int slotBooked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();

            cells[idx] = slotBooked + "/" + slotTotal;
            total += slotTotal;
            booked += slotBooked;

            if (slot.getStatus() != null && slot.getStatus() == 1) {
                normalSlots++;
            } else {
                stoppedSlots++;
            }
        }

        row.setSlot0800(orDash(cells[0]));
        row.setSlot0900(orDash(cells[1]));
        row.setSlot1000(orDash(cells[2]));
        row.setSlot1400(orDash(cells[3]));
        row.setSlot1500(orDash(cells[4]));
        row.setSlot1600(orDash(cells[5]));

        row.setTotalQuota(total);
        row.setBookedCount(booked);
        row.setRemainQuota(total - booked);
        // 一天里可能有的时段停诊、有的正常，取 head 的 status 会说谎，必须按组汇总
        row.setStatusText(stoppedSlots == 0 ? "正常"
                : (normalSlots == 0 ? "停诊" : "部分停诊"));
        return row;
    }

    @Override
    public void export(HttpServletResponse response, LocalDate scheduleDateStart, LocalDate scheduleDateEnd) {
        //1 统一时间格式
        LocalDate[] range = normaliseRange(scheduleDateStart, scheduleDateEnd);
        LocalDate from = range[0];
        LocalDate to = range[1];
        //1.1 检查时间逻辑
        if (to.isBefore(from)) {
            throw new BusinessException("导出时间范围非法：结束日期 " + to + " 早于开始日期 " + from);
        }
        //1.2 检查时间天数范围
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > maxDays) {
            throw new BusinessException("单次最多导出 " + maxDays + " 天，当前已选 " + days + " 天，请缩小范围");
        }

        //2 取数值
        PageResult<ScheduleVO> scheduleVOPageResult = scheduleService.pageAdmin(1, maxRows, null, from, to);
        //2.1 校验取值范围
        if (scheduleVOPageResult.getTotal() > maxRows) {
            throw new BusinessException("本范围内排班 " + scheduleVOPageResult.getTotal() + " 条，超出上限 " + maxRows + " 条，请缩小日期范围");
        }
        //2.2 校验内容是否非空
        List<ScheduleVO> records = scheduleVOPageResult.getRecords();
        if (records == null || records.isEmpty()) {
            throw new BusinessException("所选范围内没有排班数据");
        }

        //3透视拼接，一行一个医生，对应医生今天的排班和内容
        Map<String, List<ScheduleVO>> byDoctorDay = new LinkedHashMap<>();
        for (ScheduleVO vo : records) {
            String key = vo.getDoctorId() + "|" + vo.getScheduleDate();
            byDoctorDay.computeIfAbsent(key, k -> new ArrayList<>(SLOT_ORDER.size())).add(vo);
        }

        List<ScheduleExportRow> rows = new ArrayList<>(byDoctorDay.size());
        for (List<ScheduleVO> group : byDoctorDay.values()) {
            rows.add(toRow(group));
        }

        //4 排序：pageAdmin 是日期倒序（ScheduleServiceImpl:156），导出必须正序
        rows.sort(Comparator
                .comparing(ScheduleExportRow::getScheduleDate)
                .thenComparing(ScheduleExportRow::getDepartmentName,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ScheduleExportRow::getDoctorId));

        // 5 写出
        String baseName = FILE_BASE_NAME + "_"
                + from.format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + to.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            ExcelExporter.write(response, baseName, SHEET_NAME, ScheduleExportRow.class, rows);
        } catch (IOException e) {
            throw new BusinessException("导出文件生成失败：" + e.getMessage());
        }

        log.info("[Export] 排班时段明细导出 {} ~ {} 排班 {} 条 → 明细 {} 行",
                from, to, records.size(), rows.size());
    }


}
