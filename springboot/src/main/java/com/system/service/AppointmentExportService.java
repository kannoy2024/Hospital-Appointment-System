package com.system.service;

import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;

//管理员使用的Excel导出
public interface AppointmentExportService {
    /** 导出预约管理的全部数据*/
    void export(HttpServletResponse response, String status, LocalDate scheduleDate, String keyword);
}
