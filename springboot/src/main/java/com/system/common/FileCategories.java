package com.system.common;

import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 上传业务分类（对应 uploads 下子目录）。
 * 新增分类时：在此常量 + FileStorageServiceImpl.ruleFor + README 同步维护。
 * 这是一个上传文件分类管理类，用于规范项目中文件上传的存储路径和分类
 */
@NoArgsConstructor
public final class FileCategories {
    /**
     * 头像分类：用于用户头像上传
     * /uploads/avatar/xxx.jpg
     */
    public static final String AVATAR = "avatar";

    /**
     * 通用分类：用于普通文件上传（默认分类）
     * /uploads/common/xxx.pdf
     */
    public static final String COMMON = "common";
    private static final Set<String> ALLOWED = Set.of(AVATAR, COMMON);

    /**
     * 规范化并验证上传分类
     *
     * @param category 原始分类名称（如 "avatar", "AVATAR", " Avatar "）
     * @return 规范化后的分类名称（小写、去除首尾空格）
     * @throws BusinessException 当分类为null、空字符串或不在允许列表中时抛出
     * 功能说明：
     * 1. 如果分类为空或空白，返回默认分类 COMMON（容错处理）
     * 2. 去除首尾空格并转为小写（标准化处理）
     * 3. 验证是否在允许的分类列表中
     * 4. 返回合法的分类名称
     */
    public static String normalize(String category) {
        // 处理null或空白字符串：返回默认分类
        if (category == null || category.isBlank()) {
            return COMMON;
        }
        // 标准化：去除首尾空格并转小写
        String value = category.trim().toLowerCase();
        // 校验：如果不在允许列表中，抛出业务异常
        if (!ALLOWED.contains(value)) {
            throw new BusinessException("不支持的上传分类: " + category);
        }
        // 返回合法的分类名称
        return value;
    }
}