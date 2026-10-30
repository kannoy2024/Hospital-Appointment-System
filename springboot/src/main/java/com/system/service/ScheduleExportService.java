package com.system.service;


import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;

//排班表导出，日期范围由调用方传入
public interface ScheduleExportService {

    //导出排班时段明细
    void export(HttpServletResponse response, LocalDate scheduleDateStart,LocalDate scheduleDateEnd);
}
