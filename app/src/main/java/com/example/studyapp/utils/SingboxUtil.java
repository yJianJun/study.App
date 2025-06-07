package com.example.studyapp.utils;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class SingboxUtil {

  private static boolean isLibraryLoaded = false;

  static {
    try {
      System.loadLibrary("singbox");
      isLibraryLoaded = true;
    } catch (UnsatisfiedLinkError e) {
      Log.e("SingBox", "Failed to load singbox library.", e);
    }
  }

  public static native int StartVpn();

  public static native int StopVpn();

  private static File singBoxBinary, singBoxConfig;

  public static void startSingBox(Context context) {
    synchronized (SingboxUtil.class) { // 确保线程安全
      if (!isLibraryLoaded) {
        Log.e("SingBox", "Native library not loaded. Cannot perform StopVpn.");
        return;
      }

      if (!ensureSingBoxFilesExist(context)) {
        Log.e("SingBox", "Singbox files are missing.");
        return;
      }
        int result = StartVpn();
        if (result == 0) {
          Log.i("SingBox", "SingBox service started successfully using StartVpn.");
        } else {
          Log.e("SingBox", "Failed to start SingBox service. Return code: " + result);
        }
    }
  }

  // 简单的 JSON 验证函数示例
  private static boolean isValidJson(String json) {
    try {
      new org.json.JSONObject(json); // or new org.json.JSONArray(json);
      return true;
    } catch (org.json.JSONException ex) {
      return false;
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
        singBoxConfig = new File(context.getCodeCacheDir(), "config.json");

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

  public static void stopSingBox() {
    synchronized (SingboxUtil.class) { // 防止并发冲突
      if (!isLibraryLoaded) {
        Log.e("SingBox", "Native library not loaded. Cannot perform StopVpn.");
        return;
      }

      try {
        Log.d("SingBox", "Invoking StopVpn method on thread: " + Thread.currentThread().getName());
        int result = StopVpn();
        switch (result) {
          case 0:
            Log.i("SingBox", "SingBox service stopped successfully using StopVpn.");
            break;
          case -1:
            Log.e("SingBox", "Failed to stop SingBox: Instance is not initialized or already stopped.");
            break;
          case -2:
            Log.e("SingBox", "Failed to stop SingBox: Unexpected error occurred during the shutdown.");
            break;
          default:
            Log.e("SingBox", "Unexpected StopVpn error code: " + result);
            break;
        }
      } catch (UnsatisfiedLinkError e) {
        Log.e("SingBox", "Failed to load native library for stopping VPN.", e);
      } catch (Exception e) {
        Log.e("SingBox", "Unexpected error occurred while stopping SingBox using StopVpn.", e);
      }
    }
  }
}
