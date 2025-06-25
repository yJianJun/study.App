package com.example.studyapp.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogFileUtil {

    private static final String TAG = "LogFileUtil";

    private static File LOG_DIR; // 日志文件目录
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB 文件大小限制

    /**
     * 初始化日志文件目录（需显式调用）
     */
    public static void initialize(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LOG_DIR = new File(context.getExternalFilesDir(null), "logs"); // 应用专属目录
        } else {
            LOG_DIR = new File(Environment.getExternalStorageDirectory(), "Agent");
        }

        if (!LOG_DIR.exists() && !LOG_DIR.mkdirs()) {
            LogFileUtil.logAndWrite(Log.ERROR, TAG, "日志文件目录创建失败，路径：" + LOG_DIR.getAbsolutePath(), null);
        }
    }

    /**
     * 写入日志到文件
     */
    public static void writeLogToFile(String level, String tag, String message) {
        if (LOG_DIR == null) {
            Log.e(TAG, "日志文件目录未初始化！");
            return;
        }

        File logFile = new File(LOG_DIR, "app_logs.txt");
        if (!logFile.exists()) {
            try {
                if (!logFile.createNewFile()) {
                    Log.e(TAG, "日志文件创建失败！");
                }
            } catch (IOException e) {
                Log.e(TAG, "日志文件创建异常", e);
            }
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timeStamp + " " + level + "/" + tag + ": " + message + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(logMessage);
            checkFileSize(logFile);
        } catch (IOException e) {
            Log.e(TAG, "写入日志到文件失败", e);
        }
    }

    /**
     * 检查文件大小并清理日志内容
     */
    private static void checkFileSize(File file) {
        if (file.length() > MAX_FILE_SIZE) { // 超出大小限制
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(""); // 清空文件
                Log.i(TAG, "日志文件已清空（超出大小限制）");
            } catch (IOException e) {
                Log.e(TAG, "清空日志文件失败", e);
            }
        }
    }

    /**
     * 添加到 Android 默认日志，同时写入文件，并可记录异常信息
     */
    public static void logAndWrite(int level, String tag, String message, Throwable throwable) {
        switch (level) {
            case Log.DEBUG:
                Log.d(tag, message);
                break;
            case Log.INFO:
                Log.i(tag, message);
                break;
            case Log.WARN:
                Log.w(tag, message);
                break;
            case Log.ERROR:
                Log.e(tag, message, throwable);
                break;
            default:
                Log.v(tag, message);
                break;
        }

        String levelName;
        switch (level) {
            case Log.DEBUG:
                levelName = "DEBUG";
                break;
            case Log.INFO:
                levelName = "INFO";
                break;
            case Log.WARN:
                levelName = "WARN";
                break;
            case Log.ERROR:
                levelName = "ERROR";
                break;
            default:
                levelName = "VERBOSE";
                break;
        }

        if (throwable != null) {
            message += "\n" + Log.getStackTraceString(throwable);
        }

        writeLogToFile(levelName, tag, message);
    }
}