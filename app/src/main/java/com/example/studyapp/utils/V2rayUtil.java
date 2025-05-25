package com.example.studyapp.utils;

import android.content.Context;
import android.util.Log;

import com.example.studyapp.MainActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.io.InputStreamReader;
import java.util.List;

public class V2rayUtil {

    public static void startV2Ray(Context context) {
        // 确保文件存在
        ensureV2RayFilesExist(context);

        try {
            // 获取文件路径
            File fileDir = context.getFilesDir();
            File v2rayBinary = new File(fileDir, "v2ray/v2ray");
            File v2rayConfig = new File(fileDir, "v2ray/config.json");

            // 检查文件存在性（再次验证）
            if (!v2rayBinary.exists() || !v2rayConfig.exists()) {
                Log.e("V2Ray", "V2Ray binary or config file not found");
                return;
            }

            // 检查权限
            if (!v2rayBinary.setExecutable(true)) {
                throw new IllegalStateException("Failed to make V2Ray binary executable");
            }

            // 构建命令
            ProcessBuilder builder = new ProcessBuilder()
                    .command(v2rayBinary.getAbsolutePath(),
                            "-config",
                            v2rayConfig.getAbsolutePath())
                    .directory(fileDir);

            // 其余逻辑不变...
            Process process = builder.start();

            // 捕获输出逻辑略...
            Log.i("V2Ray", "V2Ray service started");

        } catch (Exception e) {
            Log.e("V2Ray", "Failed to start V2Ray core", e);
        }
    }

    private static void ensureV2RayFilesExist(Context context) {
        File filesDir = context.getFilesDir();
        File v2rayDir = new File(filesDir, "v2ray");
        File v2rayBinary = new File(v2rayDir, "v2ray");
        File v2rayConfig = new File(v2rayDir, "config.json");

        try {
            // 创建 v2ray 目录
            if (!v2rayDir.exists() && v2rayDir.mkdirs()) {
                Log.i("V2Ray", "Created directory: " + v2rayDir.getAbsolutePath());
            }

            // 检查并复制 v2ray 可执行文件
            if (!v2rayBinary.exists()) {
                try (InputStream input = context.getAssets().open("v2ray/v2ray");
                     FileOutputStream output = new FileOutputStream(v2rayBinary)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                    Log.i("V2Ray", "Copied v2ray binary to: " + v2rayBinary.getAbsolutePath());
                }
            }

            // 确保可执行权限
            if (!v2rayBinary.setExecutable(true)) {
                throw new IllegalStateException("Failed to make v2ray binary executable");
            }

            // 检查并复制 config.json 配置文件
            if (!v2rayConfig.exists()) {
                try (InputStream input = context.getAssets().open("v2ray/config.json");
                     FileOutputStream output = new FileOutputStream(v2rayConfig)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                    Log.i("V2Ray", "Copied v2ray config to: " + v2rayConfig.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            Log.e("V2Ray", "Failed to prepare V2Ray files", e);
        }
    }
}
