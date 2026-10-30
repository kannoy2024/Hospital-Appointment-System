package com.system.common;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

//为导出准备的通用写出工具

public class ExcelExporter {

    public ExcelExporter() {
    }

    public static void write(HttpServletResponse response,
                             String baseName,
                             String sheetName,
                             Class<?> clazz,
                             List<?> rows) throws IOException {

        String fileName = baseName + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);

        ExcelWriter writer = FastExcel.write(response.getOutputStream(), clazz)
                .autoCloseStream(false)      // 流由 Servlet 容器关闭，别让 writer 关掉
                .build();
        try {
            WriteSheet sheet = FastExcel.writerSheet(sheetName).build();
            writer.write(rows, sheet);
        } finally {
            writer.finish();                 // 必须 finally，否则文件损坏 + 连接泄漏
        }
    }
}
