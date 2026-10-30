package com.system.service.impl;

import com.system.common.BusinessException;
import com.system.common.FileCategories;
import com.system.dto_vo.vo.FileVO;
import com.system.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储服务实现：将上传文件按分类存入本地磁盘并返回统一访问路径。
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    /** 与 UploadResourceConfig 静态映射、前端 img src 保持一致 */
    private static final String PUBLIC_PREFIX = "/uploads/";

    private static final Set<String> IMAGE_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final Set<String> COMMON_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar", ".txt"
    );

    private final Path uploadRoot;

    /**
     * 注入上传根目录并规范化为绝对路径。
     *
     * @param uploadPath 上传根目录
     */
    public FileStorageServiceImpl(@Value("${app.upload.path:./uploads}") String uploadPath) {
        this.uploadRoot = Path.of(uploadPath).toAbsolutePath().normalize();
    }

    /**
     * 按分类上传文件（不指定文件名前缀）。
     *
     * @param category 文件分类
     * @param file 上传文件
     * @return 文件VO
     */
    @Override
    public FileVO upload(String category, MultipartFile file) {
        return upload(category, file, null);
    }

    /**
     * 按分类上传文件，校验类型与大小后写入磁盘并返回访问路径。
     *
     * @param category 文件分类
     * @param file 上传文件
     * @param filenamePrefix 文件名前缀（可选）
     * @return 文件VO
     */
    @Override
    public FileVO upload(String category, MultipartFile file, String filenamePrefix) {
        String cat = FileCategories.normalize(category);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }

        UploadRule rule = ruleFor(cat);
        String ext = resolveExtension(file.getOriginalFilename(), file.getContentType(), rule.allowedExt);
        if (!StringUtils.hasText(ext)) {
            throw new BusinessException("不支持的文件类型");
        }
        if (file.getSize() > rule.maxBytes) {
            throw new BusinessException("文件大小不能超过 " + (rule.maxBytes / 1024 / 1024) + "MB");
        }

        String baseName = StringUtils.hasText(filenamePrefix)
                ? filenamePrefix + "_" + System.currentTimeMillis()
                : UUID.randomUUID().toString().replace("-", "");
        String filename = baseName + ext;

        Path dir = uploadRoot.resolve(cat);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);

            String accessPath = PUBLIC_PREFIX + cat + "/" + filename;
            FileVO vo = new FileVO();
            vo.setPath(accessPath);
            vo.setUrl(accessPath);
            vo.setCategory(cat);
            vo.setOriginalFilename(file.getOriginalFilename());
            vo.setSize(file.getSize());
            return vo;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
    }

    /**
     * 将访问路径解析为本地资源，文件不存在或不可读时抛出异常。
     *
     * @param accessPath 文件访问路径
     * @return 文件资源
     */
    @Override
    public Resource loadAsResource(String accessPath) {
        Path path = resolveLocalPath(accessPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(404, "文件不可读");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException("文件路径无效");
        }
    }

    /**
     * 将访问路径解析为本地绝对路径，拒绝目录穿越等非法路径。
     *
     * @param accessPath 文件访问路径
     * @return 本地文件路径
     */
    @Override
    public Path resolveLocalPath(String accessPath) {
        if (!StringUtils.hasText(accessPath) || !accessPath.startsWith(PUBLIC_PREFIX)) {
            throw new BusinessException("无效的文件路径");
        }
        String relative = accessPath.substring(PUBLIC_PREFIX.length());
        Path path = uploadRoot.resolve(relative).normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new BusinessException("无效的文件路径");
        }
        return path;
    }

    /**
     * 校验权限后删除用户头像或管理员通用附件。
     *
     * @param accessPath 文件访问路径
     * @param userId 用户ID
     * @param role 用户角色
     */
    @Override
    public void deleteForUser(String accessPath, Long userId, String role) {
        resolveLocalPath(accessPath);
        String avatarPrefix = PUBLIC_PREFIX + FileCategories.AVATAR + "/" + userId + "_";
        if (accessPath.startsWith(avatarPrefix)) {
            deleteLocalFile(accessPath);
            return;
        }
        if (accessPath.startsWith(PUBLIC_PREFIX + FileCategories.COMMON + "/")) {
            if (!"ADMIN".equals(role)) {
                throw new BusinessException(403, "需要管理员权限");
            }
            deleteLocalFile(accessPath);
            return;
        }
        throw new BusinessException("只能删除本人头像或管理员删除通用附件");
    }

    /**
     * 直接删除本地文件，非法路径或删除失败时静默忽略。
     *
     * @param accessPath 文件访问路径
     */
    @Override
    public void deleteLocalFile(String accessPath) {
        if (!StringUtils.hasText(accessPath) || !accessPath.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        try {
            Path path = resolveLocalPath(accessPath);
            Files.deleteIfExists(path);
        } catch (BusinessException ignored) {
            // 非法路径忽略
        } catch (IOException ignored) {
            // 忽略删除失败
        }
    }

    /**
     * 获取上传根目录。
     *
     * @return 上传根目录路径
     */
    @Override
    public Path getUploadRoot() {
        return uploadRoot;
    }

    /**
     * 按分类返回允许的扩展名与大小上限，头像更严格、通用附件更宽松。
     *
     * @param category 文件分类
     * @return 上传规则
     */
    private UploadRule ruleFor(String category) {
        if (FileCategories.AVATAR.equals(category)) {
            return new UploadRule(IMAGE_EXT, 2L * 1024 * 1024);
        }
        return new UploadRule(COMMON_EXT, 10L * 1024 * 1024);
    }

    /**
     * 从文件名后缀或 MIME 类型推导合规扩展名，无法识别时返回空串。
     *
     * @param filename 原始文件名
     * @param contentType 文件 MIME 类型
     * @param allowed 允许的扩展名集合
     * @return 扩展名（含点），无法识别时为空串
     */
    private String resolveExtension(String filename, String contentType, Set<String> allowed) {
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (allowed.contains(ext)) {
                return ext;
            }
        }
        if (contentType != null) {
            String ext = switch (contentType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                case "application/pdf" -> ".pdf";
                case "text/plain" -> ".txt";
                default -> "";
            };
            if (allowed.contains(ext)) {
                return ext;
            }
        }
        return "";
    }

    private record UploadRule(Set<String> allowedExt, long maxBytes) {
    }
}
