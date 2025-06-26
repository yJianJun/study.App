package com.example.studyapp.utils;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {
    /**
     * 解压ZIP文件到指定目录
     * @param zipFile ZIP文件路径
     * @param destDir 目标目录路径
     * @throws IOException 如果解压失败
     */
    public static void unzip(String zipFile, String destDir) throws IOException {
        File dir = new File(destDir);
        // 创建目标目录（如果不存在）
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + destDir);
            }
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            byte[] buffer = new byte[8192];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                String fileName = ze.getName();
                File file = new File(destDir, fileName);

                // 创建必要的父目录
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new IOException("Failed to create parent directory: " + parent);
                    }
                }

                if (ze.isDirectory()) {
                    if (!file.mkdirs()) {
                        throw new IOException("Failed to create directory: " + file);
                    }
                } else {
                    try (FileOutputStream fos = new FileOutputStream(file);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                        while ((count = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static List<File> getAllApkFiles(String dirPath) {
        List<File> apkFiles = new ArrayList<>();
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            Log.e("APK", "目录不存在或不是目录: " + dirPath);
            return apkFiles;
        }

        File[] files = dir.listFiles();
        if (files == null) return apkFiles;

        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".apk")) {
                apkFiles.add(file);
            }
        }

        return apkFiles;
    }

    public static String buildInstallCommand(List<File> apkFiles) {
        StringBuilder cmd = new StringBuilder("pm install-multiple");

        for (File apk : apkFiles) {
            cmd.append(" \"").append(apk.getAbsolutePath()).append("\"");
        }

        return cmd.toString();
    }
}
