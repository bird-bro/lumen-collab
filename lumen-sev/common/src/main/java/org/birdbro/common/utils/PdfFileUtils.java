package org.birdbro.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * PDF 文件操作工具类
 * <p>
 * 提供 PDF 报告文件的路径解析、目录准备、原子写、读缓存、文件名清洗与 RFC 5987 编码。
 * 不引入 OpenHTMLtoPDF / Freemarker 等重型渲染依赖，保持 common 模块轻量。
 *
 * @author birdbro
 * @since 2026-08-03
 */
@Slf4j
public class PdfFileUtils {

    private PdfFileUtils() {
    }

    // ==================== 路径解析 ====================

    /**
     * 拼接并归一化存储根路径：upload.home + upload.ops.pdf
     *
     * @param uploadHome 上传根目录（如 /mnt）
     * @param opsPdfDir  PDF 子目录（如 /file/rep_pdf）
     * @return 归一化的绝对路径
     */
    public static String resolveStorageRoot(String uploadHome, String opsPdfDir) {
        if (uploadHome == null) {
            uploadHome = "/mnt";
        }
        if (opsPdfDir == null) {
            opsPdfDir = "/file/rep_pdf";
        }
        // 去除尾部斜杠
        String home = uploadHome.endsWith("/") ? uploadHome.substring(0, uploadHome.length() - 1) : uploadHome;
        // 确保子目录以 / 开头
        String dir = opsPdfDir.startsWith("/") ? opsPdfDir : "/" + opsPdfDir;
        return home + dir;
    }

    /**
     * 确保报告存储目录存在
     *
     * @param storageRoot 存储根路径
     */
    public static void ensureReportDir(String storageRoot) {
        File dir = new File(storageRoot);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("PDF报告目录已创建: {}", storageRoot);
            }
        }
    }

    /**
     * 解析报告文件路径：storageRoot/report_{tid}.pdf
     *
     * @param storageRoot 存储根路径
     * @param tid         任务ID
     * @return 报告文件绝对路径
     */
    public static String resolveReportPath(String storageRoot, Integer tid) {
        return storageRoot + File.separator + "report_" + tid + ".pdf";
    }

    // ==================== 文件读写 ====================

    /**
     * 读取已存在的 PDF 文件字节；文件不存在返回空数组
     *
     * @param filePath 文件绝对路径
     * @return PDF 字节数组，不存在返回空数组
     */
    public static byte[] readPdfIfExists(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取PDF文件失败: {}", filePath, e);
            return new byte[0];
        }
    }

    /**
     * 原子写入 PDF 文件：先写临时文件，再原子移动到目标路径
     *
     * @param filePath 目标文件绝对路径
     * @param bytes    PDF 字节数组
     * @throws IOException 写入失败
     */
    public static void atomicWritePdf(String filePath, byte[] bytes) throws IOException {
        Path target = Paths.get(filePath);
        // 确保父目录存在
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // 写临时文件
        Path tempFile = Files.createTempFile(parent, "report_", ".pdf.tmp");
        try {
            Files.write(tempFile, bytes);
            // 原子移动
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("PDF报告已原子写入: {}", filePath);
        } catch (Exception e) {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("原子写入PDF失败: " + filePath, e);
        }
    }

    // ==================== 文件名处理 ====================

    /**
     * 清洗下载文件名：移除文件名中不安全字符（/ \ : * ? " < > |）
     *
     * @param taskName 原始任务名
     * @return 清洗后的安全文件名部分
     */
    public static String sanitizeDownloadFilename(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return "";
        }
        // 移除文件名不安全字符
        return taskName.replaceAll("[/\\\\:*?\"<>|]", "");
    }

    /**
     * 构建 RFC 5987 Content-Disposition 头
     * <p>
     * 同时提供 filename= (ASCII fallback) 和 filename*= (UTF-8 精确编码)，
     * 确保中文文件名在各浏览器中正确显示。
     *
     * @param filename 下载文件名（可含中文）
     * @return Content-Disposition 头值
     */
    public static String buildContentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");  // 空格用 %20 而非 +

        // ASCII fallback：移除非 ASCII 字符
        String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_");

        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }
}
