package com.example.studyapp.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class V2rayUtil {
    private static File v2rayConfig, v2rayBinary;

    public static void startV2Ray(Context context) {
        try {
            // 确保文件存在并准备就绪
            if (!ensureV2RayFilesExist(context)) {
                Log.e("V2Ray", "V2Ray files are missing, cannot start.");
                return;
            }


            // 构建命令
            ProcessBuilder builder = new ProcessBuilder(v2rayBinary.getAbsolutePath(), "-config", v2rayConfig.getAbsolutePath()).redirectErrorStream(true);

            // 启动进程
            try {
                Process process = builder.start();
            } catch (IOException e) {
                Log.e("V2Ray", "Failed to start the process", e);
                return;
            }

            // 日志输出
            Log.i("V2Ray", "V2Ray service started");
        } catch (Exception e) {
            Log.e("V2Ray", "Failed to start V2Ray core", e);
        }
    }

    public static boolean ensureV2RayFilesExist(Context context) {

        synchronized (V2rayUtil.class) {
            try {
                // 检查并复制 v2ray 可执行文件
                String abi = Build.SUPPORTED_ABIS[0]; // 获取当前设备支持的 ABI 架构
                v2rayBinary = new File(context.getCodeCacheDir(), "v2ray");

                if (!v2rayBinary.exists()) {
                    InputStream binaryInputStream = context.getAssets().open("v2ray/" + abi + "/v2ray");
                    FileOutputStream binaryOutputStream = new FileOutputStream(v2rayBinary);
                    try {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = binaryInputStream.read(buffer)) > 0) {
                            binaryOutputStream.write(buffer, 0, length);
                        }
                        Log.i("V2Ray", "Copied v2ray binary to: " + v2rayBinary.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("V2rayUtil", "Failed to copy v2ray binary", e);
                        return false;
                    } finally {
                        binaryInputStream.close();
                        binaryOutputStream.close();
                    }
                }
                v2rayBinary.setExecutable(true, false);
                v2rayBinary.setReadable(true, false);
                v2rayBinary.setWritable(true, false);

                // 检查文件是否已经具有可执行权限
                if (!v2rayBinary.canExecute()) {
                    Log.e("V2rayUtil", "Binary file does not have execute permission. Aborting start.");
                    return false;
                }

                // 检查并复制 config.json 文件
                v2rayConfig = new File("/data/v2ray/config.json");

                File v2rayDirectory = v2rayConfig.getParentFile();
                if (v2rayDirectory != null && !v2rayDirectory.exists()) {
                    Log.e("V2rayUtil", "Failed to find directory: " + v2rayDirectory.getAbsolutePath());
                    return false; // 无法创建目录时直接返回
                }

                if (!v2rayConfig.exists()) {
                    InputStream configInputStream = context.getAssets().open("v2ray/" + abi + "/config.json");
                    FileOutputStream configOutputStream = new FileOutputStream(v2rayConfig);
                    try {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = configInputStream.read(buffer)) > 0) {
                            configOutputStream.write(buffer, 0, length);
                        }
                        Log.i("V2Ray", "Copied v2ray config.json to: " + v2rayConfig.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("V2rayUtil", "Failed to copy config.json", e);
                        return false;
                    } finally {
                        configInputStream.close();
                        configOutputStream.close();
                    }
                }
                v2rayConfig.setReadable(true, false);
                v2rayConfig.setWritable(true, false);

                return true;
            } catch (IOException e) {
                Log.e("V2Ray", "Failed to prepare V2Ray files", e);
                return false;
            }
        }
    }

    public static void stopV2Ray() {
        // 如果二进制文件不存在或不可执行，直接返回
        if (v2rayBinary == null || !v2rayBinary.exists() || !v2rayBinary.canExecute()) {
            Log.e("V2Ray", "v2rayBinary is either null, does not exist, or is not executable: " +
                    (v2rayBinary != null ? v2rayBinary.getAbsolutePath() : "null"));
            return;
        }

        // 创建新线程来处理停止任务
        new Thread(() -> {
            try {
                // 判断是否有运行中的 v2ray 进程
                if (isV2rayRunning()) {
                    String command = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "ps -A" : "ps";
                    Process psProcess = Runtime.getRuntime().exec(command); // 列出所有进程
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 检查是否是 v2ray 进程
                            if (line.contains("v2ray")) {
                                String[] parts = line.trim().split("\\s+");
                                if (parts.length > 1) {
                                    String pid = parts[1]; // 获取 PID
                                    Log.i("V2Ray", "Found V2Ray process, PID: " + pid);

                                    // 发出 kill 指令以终止进程
                                    Process killProcess = new ProcessBuilder("kill", "-9", pid).start();
                                    killProcess.waitFor(); // 等待命令完成
                                    Log.i("V2Ray", "V2Ray stopped successfully.");
                                    return; // 停止任务完成后退出
                                }
                            }
                        }
                    }
                }

                Log.i("V2Ray", "No V2Ray process is currently running.");
            } catch (IOException | InterruptedException e) {
                Log.e("V2Ray", "Error while stopping V2Ray: " + e.getMessage(), e);
            }
        }).start();
    }

    public static boolean isV2rayRunning() {
        try {
            String command = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "ps -A" : "ps";
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("v2ray")) { // 更精确匹配进程描述
                        Log.i("CustomVpnService", "V2Ray process found: " + line);
                        return true;
                    }
                }
            }
            Log.i("CustomVpnService", "No V2Ray process is running.");
        } catch (IOException e) {
            Log.e("CustomVpnService", "Error checking V2Ray process: " + e.getMessage(), e);
        }
        return false;
    }
}