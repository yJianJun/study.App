package com.example.studyapp.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class V2rayUtil {
    private  static File v2rayConfig,v2rayBinary;

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


                v2rayConfig  = new File("/data/v2ray/config.json");

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
}