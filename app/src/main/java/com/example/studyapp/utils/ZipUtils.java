package com.example.studyapp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtils {
    /**
     * 压缩目录为ZIP文件
     * @param sourceDirPath 要压缩的目录路径（如：/sdcard/MyFolder）
     * @param zipFilePath   生成的ZIP文件路径（如：/sdcard/Archive.zip）
     * @throws IOException 如果操作失败
     */
    public static void zipDirectory(String sourceDirPath, String zipFilePath) throws IOException {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new IOException("源目录不存在或不是目录");
        }

        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addDirectoryToZip(sourceDir, sourceDir, zos);
        }
    }

    /**
     * 递归添加目录内容到ZIP
     */
    private static void addDirectoryToZip(File rootDir, File currentDir, ZipOutputStream zos) throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(rootDir, file, zos); // 递归处理子目录
            } else {
                addFileToZip(rootDir, file, zos);
            }
        }
    }

    /**
     * 添加单个文件到ZIP
     */
    private static void addFileToZip(File rootDir, File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            // 计算相对路径（确保ZIP内目录结构正确）
            String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
            ZipEntry zipEntry = new ZipEntry(relativePath);
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }
}
