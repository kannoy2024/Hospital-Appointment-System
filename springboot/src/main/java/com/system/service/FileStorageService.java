package com.system.service;

import com.system.dto_vo.vo.FileVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：上传、下载、删除。实现类 {@link com.system.service.impl.FileStorageServiceImpl}。
 */
public interface FileStorageService {

    /**
     * 通用上传
     *
     * @param category 业务分类
     * @param file     文件
     */
    FileVO upload(String category, MultipartFile file);

    /**
     * 带文件名前缀的上传（如头像：用户 ID）
     */
    FileVO upload(String category, MultipartFile file, String filenamePrefix);

    /**
     * 按访问路径加载文件（用于下载）
     */
    Resource loadAsResource(String accessPath);

    /**
     * 解析访问路径对应的本地绝对路径（须在校验通过后使用）
     */
    java.nio.file.Path resolveLocalPath(String accessPath);

    /**
     * 删除本地文件（仅 /uploads/ 下路径）
     */
    void deleteLocalFile(String accessPath);

    /**
     * 按当前用户权限删除：头像仅本人；common 仅管理员。
     */
    void deleteForUser(String accessPath, Long userId, String role);

    java.nio.file.Path getUploadRoot();
}
