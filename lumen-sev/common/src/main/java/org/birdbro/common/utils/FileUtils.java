package org.birdbro.common.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author birdbro
 * @date 17:17 2024-08-02
 **/
@Slf4j
public class FileUtils {

    /**
     * 上传文件
     *
     * @author: bird
     * @date: 2022-4-7 15:34
     * @param:
     * @return:
     **/
    public static String uploadFile(MultipartFile file, String path, String rootPath) throws IOException {
        //获取文件名2.txt
        String originalFilename = file.getOriginalFilename();
        //获取文件类型
        String fileType = FileUtil.extName(originalFilename);
        if (GadgetUtils.isNotBlank(fileType)) {
            //给文件名改名,防止文件名称重复
            String fileName = UUID.randomUUID().toString() + StrUtil.DOT + fileType;
            //获取文件路径,主路径+文件名
            path = path + File.separator + fileName;
            log.info("上传文件路径：{}", rootPath + path);
            //使用huTool工具类实现文件上传
            FileUtil.writeBytes(file.getBytes(), rootPath + path);
            return path;
        } else {
            return null;
        }
    }
}
