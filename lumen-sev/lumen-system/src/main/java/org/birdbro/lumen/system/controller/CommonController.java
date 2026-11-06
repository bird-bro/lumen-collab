package org.birdbro.lumen.system.controller;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common image upload - the lumen home of the OPS
 * {@code birdbro-launch/CommonController.uploadImage} pattern 1:1 (org-logo-upload-api;
 * coordination doc org-logo-upload D1-D5). Authenticated callers only (the global
 * security chain answers "203" tokenless - no @PreAuthorize, no business permission
 * bound: a common transport, not an org-scoped facility). Transport-only: the stored
 * path is returned as {@code {path}} and NOTHING is recorded in any table (logo is a
 * 1:1 column value; the sys_attach bookkeeping layer is a recorded future direction).
 *
 * <p>Server-side validation (D4, hardened beyond OPS per user decision 2026-11-06):
 * non-empty file, extension whitelist png/jpg/jpeg/webp (case-insensitive, decided on
 * the ORIGINAL filename's extension - content bytes are not sniffed in v1), 2MB cap;
 * every rejection answers {@code A0400} with the violated rule. Storage runs through
 * the existing {@code common/FileUtils.uploadFile} (the OPS copy): the committed
 * {@code upload.avatar} prefix + {@code yyyy/MM/dd} + {@code uuid.ext} under the
 * {@code upload.home} root - the returned value (e.g.
 * {@code /profile/avatar/2026/11/06/x.png}) is directly usable as an {@code <img>}
 * src via the anonymous {@code /profile/**} static read-back (framework adaptation #7).</p>
 *
 * @author birdbro
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/common")
public class CommonController {

    /** URL-path prefix persisted as the stored value (read back under /profile/**). */
    @Value("${upload.avatar}")
    private String avatar;

    /** Disk root of the upload tree - completed into WebMvcConfig's /profile/** locations. */
    @Value("${upload.home}")
    private String home;

    /** The 2MB business cap (transport ceiling is the multipart config, 5MB, in application.yml). */
    private static final long MAX_IMAGE_BYTES = 2 * 1024L * 1024L;

    /** Accepted extensions, decided case-insensitively on the original filename. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    @PostMapping("/upload/image")
    public Map<String, String> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("A0400", "文件不能为空");
        }
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("A0400", "仅支持 png/jpg/jpeg/webp 图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("A0400", "图片大小不能超过 2MB");
        }

        String path;
        try {
            path = FileUtils.uploadFile(file, avatar + File.separator
                    + new SimpleDateFormat("yyyy/MM/dd").format(new Date()), home);
        } catch (IOException e) {
            log.error("upload image failed", e);
            throw new BusinessException("B0001", "上传文件失败: " + e.getMessage());
        }
        if (path == null) {
            // FileUtils guards a blank-extension filename; the whitelist above already
            // rejected it, so this is a defensive line, not a reachable rule.
            throw new BusinessException("A0400", "文件不能为空");
        }
        Map<String, String> pathMap = new HashMap<>(1);
        pathMap.put("path", path);
        return pathMap;
    }
}
