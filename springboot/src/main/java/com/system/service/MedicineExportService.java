package com.system.service;

import jakarta.servlet.http.HttpServletResponse;

public interface MedicineExportService {

    //导出药品明细表，支持名称关键字搜索
    void export(HttpServletResponse response, String keyword);
}
