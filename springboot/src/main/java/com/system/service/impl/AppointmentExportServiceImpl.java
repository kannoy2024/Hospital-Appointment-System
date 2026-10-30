package com.system.service.impl;

import com.system.common.*;
import com.system.dto_vo.excel.AppointmentExportRow;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.entity.User;
import com.system.mapper.UserMapper;
import com.system.service.AppointmentExportService;
import com.system.service.AppointmentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentExportServiceImpl implements AppointmentExportService {


    private static final String FILE_BASE_NAME = "预约明细";
    private static final String SHEET_NAME = "预约明细";
    private static final String YES = "是";
    private static final String NO = "否";

    // 预约状态中文映射。
    private static final Map<String, String> STATUS_TEXT = Map.of(
            "unpaid", "待支付",
            "pending", "待就诊",
            "confirmed", "已确认",
            "checked_in", "已到院",
            "in_consultation", "就诊中",
            "pending_rx_pay", "待缴药费",
            "cancelled", "已取消",
            "completed", "已完成"
    );

    // 处方状态中文映射
    private static final Map<String, String> RX_STATUS_TEXT = Map.of(
            "unpaid", "待缴药费",
            "paid", "已缴费",
            "dispensed", "已取药"
    );
    private final AppointmentService appointmentService;
    private final UserMapper userMapper;
    //
    @Value("${app.export.max-rows:5000}")
    private int maxRows;

    @Override
    public void export(HttpServletResponse response, String status, LocalDate scheduleDate, String keyword) {

        //1取出需要导出的数据，复用page的内容
        PageResult<AppointmentVO> page = appointmentService.pageAdmin(1, maxRows, status, scheduleDate, keyword);

        //2校验是否超出长度
        if (page.getTotal() > maxRows) {
            throw new BusinessException("数据导出" + page.getTotal() + "条，超出导出上限" + maxRows + "请缩小范围");
        }

        //3判空
        List<AppointmentVO> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            throw new BusinessException("当前条件下没有可以导出的数据，请调整条件");
        }

        //4预约表中没有当前用户的昵称，需要补充
        Map<Long, String> nickMap = loadNicknames(records);

        //5对内容进行vo转换
        List<AppointmentExportRow> rows = records.stream()
                .map(vo -> toRow(vo, nickMap))
                .toList();

        //6将内容写出
        try {
            ExcelExporter.write(response, FILE_BASE_NAME, SHEET_NAME, AppointmentExportRow.class, rows);
        } catch (IOException e) {
            throw new BusinessException("导出文件生成失败：" + e.getMessage());
        }

        // ⑤ @Log 记不到筛选参数（只取 requestURI），这里补一条便于追查导出口径
        log.info("[Export] 预约明细导出 status={} scheduleDate={} keyword={} 行数={}",
                status, scheduleDate, keyword, rows.size());
    }

    //去User表查询用户昵称并拼接
    private Map<Long, String> loadNicknames(List<AppointmentVO> records) {
        //充预约筛选中拿到全部需要拼装用户昵称的id
        Set<Long> userIds = records.stream()
                .map(AppointmentVO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        //如果啥都没有就直接返回
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "" : u.getNickname()));
    }

    /**
     * VO → 导出行：脱敏、状态翻译、分转元都在这一步完成
     */
    private AppointmentExportRow toRow(AppointmentVO vo, Map<Long, String> nickMap) {
        AppointmentExportRow r = new AppointmentExportRow();
        r.setId(vo.getId());
        r.setScheduleDate(vo.getScheduleDate());
        r.setTimeSlot(vo.getTimeSlot());
        r.setDepartmentName(vo.getDepartmentName());
        r.setDoctorName(vo.getDoctorName());
        r.setDoctorTitle(vo.getDoctorTitle());
        r.setPatientName(vo.getPatientName());

        // 脱敏
        r.setPatientPhone(PrivacyUtils.maskPhone(vo.getPatientPhone()));

        // 昵称：userId 为空时给空串，避免 Map.of() 对 null key 抛 NPE
        r.setUserNickname(vo.getUserId() == null ? "" : nickMap.getOrDefault(vo.getUserId(), ""));

        // 分 → 元（Long 可空，必须先判 null，不能直接拆箱）
        r.setAmountYuan(vo.getAmountFen() == null ? null : MoneyUtils.fenToYuan(vo.getAmountFen()));
        r.setPrescriptionAmountYuan(vo.getPrescriptionAmountFen() == null
                ? null : MoneyUtils.fenToYuan(vo.getPrescriptionAmountFen()));

        r.setPayMethodText(vo.getPayMethodText());
        r.setPayTime(vo.getPayTime());

        // 对数字状态转成文字
        r.setStatusText(STATUS_TEXT.getOrDefault(vo.getStatus(), vo.getStatus()));
        r.setPrescriptionStatusText(vo.getPrescriptionStatus() == null
                ? "" : RX_STATUS_TEXT.getOrDefault(vo.getPrescriptionStatus(), vo.getPrescriptionStatus()));
        r.setEvaluatedText(Boolean.TRUE.equals(vo.getEvaluated()) ? YES : NO);
        r.setCreateTime(vo.getCreateTime());
        r.setRemark(vo.getRemark());
        return r;
    }
}
