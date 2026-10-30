package com.system.service.impl;

import com.system.common.BusinessException;
import com.system.common.ExcelExporter;
import com.system.common.MoneyUtils;
import com.system.common.PageResult;
import com.system.dto_vo.excel.MedicineExportRow;
import com.system.dto_vo.vo.MedicineVO;
import com.system.service.MedicineExportService;
import com.system.service.MedicineService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineExportServiceImpl implements MedicineExportService {

    private static final String FILE_BASE_NAME = "药品明细";
    private static final String SHEET_NAME = "药品明细";


    private final MedicineService medicineService;

    @Value("${app.export.max-rows:5000}")
    private int maxRows;

    private MedicineExportRow toRow(MedicineVO vo) {
        MedicineExportRow m = new MedicineExportRow();
        m.setId(vo.getId());
        m.setName(vo.getName());
        m.setSpec(vo.getSpec());
        m.setUnit(vo.getUnit());
        m.setStock(vo.getStock());
        m.setUsageDesc(vo.getUsageDesc());
        m.setPriceYuan(vo.getPriceFen() == null ? null : MoneyUtils.fenToYuan(vo.getPriceFen()));
        m.setStatusText(vo.getStatus() != null && vo.getStatus() == 1 ? "启用" : "停用");
        m.setCreateTime(vo.getCreateTime());
        return m;
    }

    @Override
    public void export(HttpServletResponse response, String keyword) {
        //1 取出全部需要导出的数据内容，复用pageAdmin
        PageResult<MedicineVO> medicineVOPageResult = medicineService.pageAdmin(1, maxRows, keyword);

        //2 检查最大长度
        if (medicineVOPageResult.getTotal() > maxRows) {
            throw new BusinessException("数据导出" + medicineVOPageResult.getTotal() + "条，超出导出上限" + maxRows + "请缩小范围");
        }

        //3 判空
        List<MedicineVO> records = medicineVOPageResult.getRecords();
        if (records == null || records.isEmpty()) {
            throw new BusinessException("当前条件下没有可以导出的数据，请调整条件");
        }

        //4 对内容进行VO转换
        List<MedicineExportRow> rows = records.stream().map(this::toRow).toList();
        try {
            ExcelExporter.write(response, FILE_BASE_NAME, SHEET_NAME, MedicineExportRow.class, rows);
        } catch (IOException e) {
            throw new BusinessException("导出文件生成失败：" + e.getMessage());
        }
        //5 记log
        // ⑤ @Log 记不到筛选参数（只取 requestURI），这里补一条便于追查导出口径
        log.info("[Export] 药品明细导出 keyword={} 行数={}",
                keyword, rows.size());

    }


}
