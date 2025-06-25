package com.example.studyapp.utils;

import java.io.File;

public class FileUtils {
    /**
     * 获取文件的父目录路径
     * @param filePath 文件完整路径
     * @return 父目录路径，如果失败返回null
     */
    public static String getParentDirectory(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        File file = new File(filePath);
        File parent = file.getParentFile();

        return parent != null ? parent.getAbsolutePath() : null;
    }

    /**
     * 获取文件的父目录File对象
     * @param file 文件对象
     * @return 父目录File对象
     */
    public static File getParentDirectory(File file) {
        return file != null ? file.getParentFile() : null;
    }

    /**
     * 获取多级上级目录
     * @param file 文件对象
     * @param levels 向上追溯的层级数
     * @return 上级目录路径
     */
    public static String getAncestorDirectory(File file, int levels) {
        if (file == null || levels <= 0) {
            return file != null ? file.getAbsolutePath() : null;
        }

        File parent = file;
        for (int i = 0; i < levels; i++) {
            parent = parent.getParentFile();
            if (parent == null) {
                break;
            }
        }

        return parent != null ? parent.getAbsolutePath() : null;
    }
}
