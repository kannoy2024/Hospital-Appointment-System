package com.system.controller;

import com.system.common.FileCategories;
import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.FileVO;
import com.system.security.SecurityUtils;
import com.system.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 文件管理控制器
 * 提供通用文件的：上传、下载、预览、删除功能

 * 设计理念：
 * 1. 统一的文件分类管理（avatar/common）
 * 2. 自动记录操作日志（@Log注解）
 * 3. 完善的API文档（Swagger注解）
 * 4. 安全的文件访问控制
 */
@Tag(name = "文件管理", description = "通用文件上传、下载、预览、删除")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)  // 要求所有接口需要JWT认证
@RestController
@RequestMapping("/api/files")
@AllArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传文件
     * 业务流程：
     * 1. 验证文件分类（avatar/common）
     * 2. 如果是头像，自动添加用户ID作为前缀
     * 3. 调用存储服务保存文件
     * 4. 返回文件访问路径
     *
     * @param file 上传的文件（MultipartFile）
     * @param category 文件分类（默认common）
     * @return 包含文件路径的统一响应
     */
    @Log(module = "文件管理", action = "上传文件")  // 自动记录操作日志
    @Operation(summary = "上传文件", description = """
            上传文件到服务器本地目录。
            - **avatar**：头像，仅图片，≤2MB，文件名自动带用户 ID 前缀
            - **common**：通用附件，图片/Office/PDF/ZIP 等，≤10MB
            返回 path 可用于展示（/uploads/...）或绑定头像。
            """)
    @PostMapping("/upload")
    public Result<FileVO> upload(
            @Parameter(description = "上传的文件")
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "分类：avatar | common，默认 common")
            @RequestParam(value = "category", defaultValue = "common") String category) {

        // 1. 标准化分类（空值/非法值处理）
        String cat = FileCategories.normalize(category);

        // 2. 头像分类：自动关联当前用户ID
        if (FileCategories.AVATAR.equals(cat)) {
            Long userId = SecurityUtils.getCurrentUserEntity().getId();
            return Result.ok(fileStorageService.upload(cat, file, String.valueOf(userId)));
        }

        // 3. 通用分类：直接上传
        return Result.ok(fileStorageService.upload(cat, file));
    }

    /**
     * 下载文件
     * 关键点：
     * 1. 使用附件方式下载（Content-Disposition: attachment）
     * 2. 文件名URL编码（支持中文）
     * 3. 返回Resource供Spring处理
     *
     * @param path 文件路径（如：/uploads/common/xxx.pdf）
     * @return 文件下载响应
     */
    @Operation(summary = "下载文件", description = "以附件形式下载，path 为上传接口返回的路径，如 /uploads/common/xxx.pdf")
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @Parameter(description = "文件访问路径", example = "/uploads/common/xxx.jpg")
            @RequestParam String path) {

        // 1. 加载文件资源
        Resource resource = fileStorageService.loadAsResource(path);

        // 2. 获取本地文件路径并提取文件名
        Path localPath = fileStorageService.resolveLocalPath(path);
        String filename = localPath.getFileName().toString();

        // 3. 文件名编码（处理中文/特殊字符）
        // 使用 RFC 5987 标准编码：filename*=UTF-8''encoded_filename
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");  // 将+替换为%20（空格）

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)  // 二进制流
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    /**
     * 预览文件（在浏览器中直接打开）
     * 与下载的区别：
     * - download：附件形式（保存到本地）
     * - preview：内联形式（在浏览器中直接展示）
     * 支持预览的类型：
     * - 图片：jpg/jpeg/png/gif/webp
     * - 文档：pdf
     * - 文本：txt
     *
     * @param path 文件路径
     * @return 文件预览响应
     */
    @Operation(summary = "预览文件", description = "浏览器内嵌打开（inline），适用于图片、PDF 等。")
    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(
            @Parameter(description = "文件访问路径")
            @RequestParam String path) {

        Resource resource = fileStorageService.loadAsResource(path);

        // 根据文件扩展名猜测Content-Type
        MediaType mediaType = probeMediaType(path);

        return ResponseEntity.ok()
                .contentType(mediaType)  // 根据文件类型设置正确的MIME
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")  // inline：浏览器内嵌展示
                .body(resource);
    }

    /**
     * 删除文件
     * 安全设计：
     * 1. 获取当前用户信息
     * 2. 传递给服务层进行权限验证
     * 3. 只有文件所有者或管理员可以删除
     *
     * @param path 文件路径
     * @return 操作结果
     */
    @Log(module = "文件管理", action = "删除文件")
    @Operation(summary = "删除文件", description = "删除服务器上的本地文件，path 须为 /uploads/ 下的路径。")
    @DeleteMapping
    public Result<Void> delete(
            @Parameter(description = "文件访问路径")
            @RequestParam String path) {
        // 获取当前用户信息用于权限验证
        var user = SecurityUtils.getCurrentUserEntity();
        fileStorageService.deleteForUser(path, user.getId(), user.getRole());
        return Result.ok();
    }

    /**
     * 根据文件路径猜测MediaType（Content-Type）
     * 为什么需要手动处理？
     * - 虽然Spring可以自动探测，但手动控制更精确
     * - 可以自定义类型映射（如webp）
     * - 避免依赖文件系统或Content-Type解析库
     *
     * @param path 文件路径
     * @return 对应的MediaType，未知类型返回APPLICATION_OCTET_STREAM
     */
    private MediaType probeMediaType(String path) {
        if (!StringUtils.hasText(path)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String lower = path.toLowerCase();

        // 图片类型
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }

        // 文档类型
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }

        // 文本类型
        if (lower.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN;
        }

        // 默认：二进制流（触发下载）
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}