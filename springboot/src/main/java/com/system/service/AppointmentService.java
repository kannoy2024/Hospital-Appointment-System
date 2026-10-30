package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.AppointmentCreateRequest;
import com.system.dto_vo.dto.AppointmentReviewRequest;
import com.system.dto_vo.vo.AppointmentPrepayVO;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.entity.Appointment;

import java.time.LocalDate;

/**
 * 预约挂号核心业务接口
 * 定义预约全生命周期操作：创建 → 支付 → 签到 → 就诊 → 完成（含处方缴费）。
 * 号源扣减优先走 Redis + Lua 脚本，Redis 不可用时降级 MySQL 乐观锁。
 */
public interface AppointmentService {

    /** 创建预约，生成 unpaid 订单，通过 Redis/MySQL 扣减号源 */
    AppointmentVO create(AppointmentCreateRequest request);

    /** 支付挂号费，unpaid → pending，仅余额支付（微信/支付宝走 prepay） */
    AppointmentVO payAppointment(Long id, String payMethod);

    /**
     * 发起预支付（微信/支付宝）：创建支付单并返回调起支付所需参数。
     *
     * <p>这里**不修改预约状态**——预约仍为 unpaid，只有网关回调到达后
     * 才由 AppointmentPayHandler 推进到 pending。前端拿到 outTradeNo 后轮询
     * {@code GET /api/payments/{outTradeNo}} 获取最终结果。
     *
     * @param id        预约ID
     * @param payMethod 支付方式（wechat/alipay）
     * @return 预支付参数（含支付单号、收银台/调起参数、过期时间）
     */
    AppointmentPrepayVO prepay(Long id, String payMethod);

    /** 患者端分页查自己预约，按状态 Tab 筛选 */
    PageResult<AppointmentVO> pageMine(long current, long size, String status);

    /** 患者查单个预约详情 */
    AppointmentVO getMineById(Long id);

    /** 患者取消，释放号源，余额支付时退款 */
    void cancelByUser(Long id);

    /** 管理员分页查所有预约，支持状态/日期/关键字筛选 */
    PageResult<AppointmentVO> pageAdmin(long current,
                                        long size,
                                        String status,
                                        LocalDate scheduleDate,
                                        String keyword);

    /** 管理员取消预约 */
    void cancelByAdmin(Long id);

    /** 管理员标记完成（发药 / 直接完成 / 校验病历和处方后完成） */
    void completeByAdmin(Long id);

    /** 患者签到，就诊时间段前 1 小时内可签 */
    void checkInByUser(Long id);

    /** 医生开始接诊，前置条件：患者已签到 */
    void startConsultation(Long appointmentId, Long doctorId);

    /** 医生完成就诊，校验病历完整后完成 */
    void completeClinical(Long appointmentId, Long doctorId);

    /** 患者评价，仅 completed 可评，不可重复 */
    void submitReview(Long appointmentId, AppointmentReviewRequest request);

    /** 实体转 VO（含排班、医生、科室、处方、病历） */
    AppointmentVO mapToVO(Appointment appointment);
}
