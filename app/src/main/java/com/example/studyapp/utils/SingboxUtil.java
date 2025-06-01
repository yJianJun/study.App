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

public class SingboxUtil {

  private static File singBoxBinary, singBoxConfig;

  public static void startSingBox(Context context) {
    if (isSingBoxRunning(context)) {
      Log.i("SingBox", "singbox is already running. Skipping start.");
      return;
    }

    if (!ensureSingBoxFilesExist(context)) {
      Log.e("SingBox", "Singbox files are missing.");
      return;
    }

    try {
      ProcessBuilder builder = new ProcessBuilder(
          singBoxBinary.getAbsolutePath(), "run", "-c", singBoxConfig.getAbsolutePath()
      ).redirectErrorStream(true);

      Process process = builder.start();
      Log.i("SingBox", "SingBox service started.");

      new Thread(() -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            Log.d("SingBox", line);
          }
        } catch (IOException e) {
          Log.e("SingBox", "Error reading process output", e);
        }
      }).start();

      int exitCode = process.waitFor(); // 等待进程完成
      Log.i("SingBox", "SingBox process exited with code: " + exitCode);

    } catch (IOException e) {
      Log.e("SingBox", "Failed to start SingBox process", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Log.e("SingBox", "Process was interrupted", e);
    }
  }

  // 检查并复制 singbox 必需文件
  public static boolean ensureSingBoxFilesExist(Context context) {
    synchronized (SingboxUtil.class) {
      try {
        // 检查并复制 singbox 可执行文件
        String abi = Build.SUPPORTED_ABIS[0]; // 获取当前设备支持的 ABI 架构
        singBoxBinary = new File(context.getCodeCacheDir(), "singbox");

        // 复制二进制文件
        if (!singBoxBinary.exists()) {
          try (InputStream binaryInputStream = context.getAssets().open("singbox/" + abi + "/sing-box");
              FileOutputStream binaryOutputStream = new FileOutputStream(singBoxBinary)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = binaryInputStream.read(buffer)) > 0) {
              binaryOutputStream.write(buffer, 0, length);
            }
            Log.i("SingBox", "Copied singbox binary to: " + singBoxBinary.getAbsolutePath());
          } catch (Exception e) {
            Log.e("SingboxUtil", "Failed to copy singbox binary", e);
            return false;
          }
        }

        // 设置执行权限
        singBoxBinary.setExecutable(true);
        singBoxBinary.setReadable(true);
        singBoxBinary.setWritable(true);

        if (!singBoxBinary.canExecute()) {
          Log.e("SingboxUtil", "Binary file does not have execute permission. Aborting start.");
          return false;
        }

        // 检查并复制配置文件
        singBoxConfig = new File("/data/singbox/config.json");

        if (!singBoxConfig.exists()) {
          try (InputStream configInputStream = context.getAssets().open("singbox/" + abi + "/config.json");
              FileOutputStream configOutputStream = new FileOutputStream(singBoxConfig)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = configInputStream.read(buffer)) > 0) {
              configOutputStream.write(buffer, 0, length);
            }
            Log.i("SingBox", "Copied singbox config.json to: " + singBoxConfig.getAbsolutePath());
          } catch (Exception e) {
            Log.e("SingboxUtil", "Failed to copy config.json", e);
            return false;
          }
        }

        singBoxConfig.setReadable(true);
        singBoxConfig.setWritable(true);

        return true;
      } catch (Exception e) {
        Log.e("SingboxUtil", "Error in ensureSingBoxFilesExist method", e);
      }
    }
    return false;
  }

  // 判断是否有正在运行的 singbox
  public static boolean isSingBoxRunning(Context context) {
      android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      java.util.List<android.app.ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
      if (runningProcesses != null) {
          for (android.app.ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
              if (processInfo.processName.contains("sing-box")) {
                  return true;
              }
          }
      }
      return false;
  }

  public static void stopSingBox() {
    Process process = null;
    try {
      // 检查是否支持 `pkill` 并停止 sing-box 进程
      ProcessBuilder builder = new ProcessBuilder("pkill", "-f", "sing-box");
      process = builder.start();

      // 等待进程执行完成
      int exitCode = process.waitFor();
      if (exitCode == 0) {
        Log.i("SingBox", "singbox process stopped successfully.");
      } else {
        Log.e("SingBox", "Failed to stop singbox process. Exit code: " + exitCode);
      }
    } catch (IOException e) {
      Log.e("SingBox", "IOException occurred while trying to stop singbox", e);
    } catch (InterruptedException e) {
      Log.e("SingBox", "The stopSingBox process was interrupted", e);
      Thread.currentThread().interrupt(); // 恢复中断状态
    } catch (Exception e) {
      Log.e("SingBox", "Unexpected error occurred", e);
    } finally {
      // 确保关闭流以避免资源泄露
      if (process != null) {
        try {
          process.getInputStream().close();
          process.getErrorStream().close();
        } catch (IOException e) {
          Log.e("SingBox", "Failed to close process streams", e);
        }
      }
    }
  }
}