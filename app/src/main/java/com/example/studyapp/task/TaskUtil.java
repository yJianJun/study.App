package com.example.studyapp.task;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import com.example.studyapp.MainActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * @Time: 2025/6/16 11:11
 * @Creator: 初屿贤
 * @File: TaskUtil
 * @Project: study.App
 * @Description:
 */
public class TaskUtil {

  private static volatile DeviceInfo deviceInfo;
  private static volatile BigoInfo bigoDevice;
  private static volatile AfInfo afDevice;

  private static final String BASE_URL = "http://47.238.96.231:8112";
  private static OkHttpClient okHttpClient = new OkHttpClient();

  private static void postDeviceInfo(String androidId) {
    if (okHttpClient == null) {
      throw new IllegalStateException("HttpClient is not initialized");
    }
    if (BASE_URL == null || BASE_URL.isEmpty()) {
      throw new IllegalStateException("BASE_URL is not initialized");
    }
    if (bigoDevice == null || afDevice == null || deviceInfo == null) {
      throw new IllegalStateException("Device information is missing");
    }

    Payload payload = new Payload();
    payload.bigoDeviceObject = bigoDevice;
    payload.afDeviceObject = afDevice;
    payload.other = deviceInfo;

    Gson gson = new GsonBuilder().serializeNulls().create();
    String jsonRequestBody = gson.toJson(payload);

    HttpUrl url = HttpUrl.parse(BASE_URL)
        .newBuilder()
        .addPathSegment("device_info_upload")
        .addQueryParameter("id", androidId)
        .build();

    RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), jsonRequestBody);

    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .build();

    okHttpClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NotNull Call call, @NotNull IOException e) {
        System.err.println("Request failed: " + e.getMessage());
        // Optional: Add retry logic
      }

      @Override
      public void onResponse(@NotNull Call call, @NotNull Response response) {
        try (ResponseBody responseBody = response.body()) {
          if (!response.isSuccessful()) {
            System.err.println("Request failed with status: " + response.code() + ", message: " + response.message());
            return;
          }
          if (responseBody != null) {
            System.out.println(responseBody.string());
          } else {
            System.err.println("Response body is null");
          }
        } catch (Exception e) {
          System.err.println("Error while processing response: " + e.getMessage());
        }
      }
    });
  }

  public static void infoUpload(Context context, String androidId, String packAge) throws IOException {

    if (context == null) {
      throw new IllegalArgumentException("Context or Package name cannot be null or empty");
    }

    if (androidId == null || androidId.isEmpty()) {
      System.err.println("ANDROID_ID is null or empty");
      return;
    }
    PackageInfo packageInfo;
    try {
      // 根据包名获取APK路径
      packageInfo = context.getPackageManager().getPackageInfo(packAge, 0);
      if (packageInfo == null) {
        throw new IllegalStateException("未找到包名对应的信息：" + packAge);
      }
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException("未找到包名对应的应用：" + packAge, e);
    }

    String apkSourceDir = packageInfo.applicationInfo.sourceDir; // APK路径
    String outputZipPath = new File(
        context.getExternalFilesDir(null),
        androidId + "_" + packAge + ".zip"
    ).getAbsolutePath(); // 压缩文件输出路径

    File zipFile = new File(outputZipPath);
    if (zipFile.exists() && !zipFile.delete()) {
      System.err.println("旧的压缩文件无法删除：" + outputZipPath);
      return;
    }

    File sourceApk = new File(apkSourceDir);
    File copiedApk = new File(context.getCacheDir(), packAge + "_temp.apk");
    if (copiedApk.exists() && !copiedApk.delete()) {
      System.err.println("旧的临时apk文件无法删除：" + copiedApk.getAbsolutePath());
      return;
    }
    Files.copy(sourceApk.toPath(), copiedApk.toPath());

    // 压缩APK文件
    try (
        FileInputStream fis = new FileInputStream(copiedApk);
        FileOutputStream fos = new FileOutputStream(outputZipPath);
        ZipOutputStream zipOut = new ZipOutputStream(fos)) {

      ZipEntry zipEntry = new ZipEntry(new File(apkSourceDir).getName());
      zipOut.putNextEntry(zipEntry);
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = fis.read(buffer)) >= 0) {
        zipOut.write(buffer, 0, bytesRead);
      }
      System.out.println("APK文件成功压缩至：" + outputZipPath);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    // 上传压缩文件
    File fileToUpload = new File(outputZipPath);
    if (!fileToUpload.exists()) {
      System.out.println("上传文件不存在：" + outputZipPath);
      return;
    }

    RequestBody fileBody = RequestBody.create(
        MediaType.get("application/zip"), fileToUpload
    );
    MultipartBody requestBody = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", fileToUpload.getName(), fileBody)
        .build();

    Request request = new Request.Builder()
        .url(BASE_URL + "/tar_info_upload")
        .post(requestBody)
        .build();

    okHttpClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NotNull Call call, @NotNull IOException e) {
        e.printStackTrace();
        System.out.println("上传失败: " + e.getMessage());
      }

      @Override
      public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
        try {
          if (response.isSuccessful()) {
            System.out.println("文件上传成功: " + response.body().string());
          } else {
            System.out.println("上传失败: " + response.message());
          }
        } finally {
          response.close(); // 确保关闭 response 以避免资源泄漏
        }
      }
    });
  }

  private static void validate() {
    if (okHttpClient == null) {
      throw new IllegalStateException("HttpClient is not initialized");
    }
    if (BASE_URL == null || BASE_URL.isEmpty()) {
      throw new IllegalStateException("BASE_URL is not initialized");
    }
  }

  private static String getDeviceInfoSync(String androidId) {
    validate(); // 检查 BASE_URL 和 okHttpClient 的合法性

    HttpUrl url = HttpUrl.parse(BASE_URL + "/device_info_download")
        .newBuilder()
        .addQueryParameter("androidId", androidId)
        .build();

    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) { // 检查响应状态
        throw new IOException("Unexpected response: " + response);
      }
      ResponseBody body = response.body();
      if (body != null) {
        return body.string();
      } else {
        throw new IOException("Response body is null");
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null; // 或者抛出上层处理
    }
  }

  private static void infoDownload(String androidId) {
    // 下载压缩包
    HttpUrl url = HttpUrl.parse(BASE_URL + "/tar_info_download")
        .newBuilder()
        .addQueryParameter("file_name", "test")
        .build();

    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    okHttpClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NotNull Call call, @NotNull IOException e) {
        e.printStackTrace();
      }

      @Override
      public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
        if (response.isSuccessful()) {
          byte[] fileBytes = response.body().bytes();
          String savePath = "/storage/emulated/0/Download/test.zip";

          File file = new File(savePath);
          file.getParentFile().mkdirs();

          try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(fileBytes);
            System.out.println("File saved to " + savePath);
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          System.out.println("Download failed: " + response.message());
        }
      }
    });
  }

  public static String execQueryTask(Context context) {
    String androidId = Settings.Secure.getString(
        context.getContentResolver(),
        Settings.Secure.ANDROID_ID
    );
    return getDeviceInfoSync(androidId);
  }

  public static void execSaveTask(Context context) {
    if (context == null) {
      throw new IllegalArgumentException("Context or Package name cannot be null or empty");
    }

    if (MainActivity.androidId == null || MainActivity.androidId.isEmpty()) {
      System.err.println("ANDROID_ID is null or empty");
      return;
    }

    try {
      postDeviceInfo(MainActivity.androidId);
    } catch (Exception e) {
      System.err.println("Error in execReloginTask: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void setHttpClient(OkHttpClient client) {
    okHttpClient = client;
  }

  public static void setDeviceInfo(DeviceInfo info) {
    deviceInfo = info;
  }

  public static void setBigoDevice(BigoInfo info) {
    bigoDevice = info;
  }

  public static void setAfDevice(AfInfo info) {
    afDevice = info;
  }
}

class Payload {

  BigoInfo bigoDeviceObject;
  AfInfo afDeviceObject;
  DeviceInfo other;
}

