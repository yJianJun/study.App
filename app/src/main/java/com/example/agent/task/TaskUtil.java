package com.example.agent.task;

import android.content.Context;
import android.util.Log;
import com.example.agent.utils.LogFileUtil;
import com.example.agent.utils.ShellUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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

  public static void postDeviceInfo(String androidId, String taskId,String packageName) {
    Log.i("TaskUtil", "postDeviceInfo called with androidId: " + androidId);

    if (okHttpClient == null) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "HttpClient is not initialized", null);
      throw new IllegalStateException("HttpClient is not initialized");
    }

    if (BASE_URL == null || BASE_URL.isEmpty()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "BASE_URL is not initialized", null);
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

    if (packageName == null){
      packageName = "";
    }
    HttpUrl url = HttpUrl.parse(BASE_URL)
        .newBuilder()
        .addPathSegment("device_info_upload")
        .addQueryParameter("id", androidId)
        .addQueryParameter("taskId", taskId)
        .addQueryParameter("packageName", packageName)
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
          LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Request failed with status: " + response.code(), null);
          return;
        }

        if (responseBody != null) {
          String responseText = responseBody.string();
          Log.i("TaskUtil", "Request succeeded. Response: " + responseText);
        } else {
          LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Response body is null", null);
        }
      } catch (IOException e) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error while processing response: " + e.getMessage(), e);
      }
    } catch (IOException e) {
     LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error during HTTP request: " + e.getMessage(), e);
    }
  }

  public static String getDeviceInfoSync(String androidId,String taskId) {
    Log.d("TaskUtil", "getDeviceInfoSync called with androidId: " + androidId);

    validate(); // 检查 BASE_URL 和 okHttpClient 的合法性

    HttpUrl url = HttpUrl.parse(BASE_URL + "/device_info_download")
        .newBuilder()
        .addQueryParameter("androidId", androidId)
        .addQueryParameter("taskId",taskId)
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
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", errorMessage, null);
        throw new IOException(errorMessage);
      }

      ResponseBody responseBody = response.body();
      if (responseBody != null) {
        String responseString = responseBody.string();
        Log.i("TaskUtil", "Received response: " + responseString);
        return responseString;
      } else {
        String errorMessage = "Response body is null: URL=" + url.toString();
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", errorMessage, null);
        throw new IOException(errorMessage);
      }
    } catch (IOException e) {
      String errorMessage = "Error during HTTP request. URL=" + url.toString() +
          ", Android ID=" + androidId;
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", errorMessage, e);
      return null; // 或考虑在上层抛出异常
    }
  }

  public static String getApkPath(String packageName) {
    if (packageName == null || packageName.trim().isEmpty()) {
      throw new IllegalArgumentException("Package name must not be null or empty");
    }

    String command = "adb shell pm path " + packageName;
    String result = ShellUtils.execRootCmdAndGetResult(command);

    if (result != null && result.startsWith("package:")) {
      return result.substring("package:".length()).trim();
    }

    return null; // 如果获取失败返回 null
  }


  public static void infoUpload(Context context, String androidId, String packAge) throws IOException {

    Log.i("TaskUtil", "infoUpload called with androidId: " + androidId + ", package: " + packAge);

    if (packAge == null || packAge.isEmpty()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Package name is null or empty", null);
      throw new IllegalArgumentException("Package name cannot be null or empty");
    }

    if (context == null) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Context is null", null);
      throw new IllegalArgumentException("Context cannot be null");
    }

    if (androidId == null || androidId.isEmpty()) {
      Log.w("TaskUtil", "ANDROID_ID is null or empty");
      return;
    }

    String  apkSourceFile = getApkPath(packAge);
    Log.d("TaskUtil", "APK source directory: " + apkSourceFile);

    File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "External storage directory is unavailable", null);
      throw new IOException("External storage directory is unavailable");
    }

    String outputZipPath = new File(externalDir, androidId + "_" + packAge + ".zip").getAbsolutePath();
    Log.d("TaskUtil", "Output ZIP path: " + outputZipPath);

    File zipFile = new File(outputZipPath);
    if (zipFile.exists()) {
      delFileSh(zipFile.getAbsolutePath());
    }
    File copiedAPKFile = new File(context.getCacheDir(), packAge+"_upload.apk");
    if (copiedAPKFile.exists()) {
      delFileSh(copiedAPKFile.getAbsolutePath());
    }
    copyFolderSh(apkSourceFile, copiedAPKFile.getAbsolutePath());
    // boolean success = clearUpFileInDst(copiedDir);
    // if (success) {
    //   // 压缩APK文件
    //   compressToZip(copiedDir, zipFile);
    // }

    // 压缩APK文件
    compressToZip(copiedAPKFile, zipFile);
    // 上传压缩文件
    if (!zipFile.exists()) {
      Log.w("TaskUtil", "Upload file does not exist: " + outputZipPath);
      return;
    }

    uploadFile(zipFile);

    // 清理临时文件
    delFileSh(copiedAPKFile.getAbsolutePath());
    delFileSh(zipFile.getAbsolutePath());
  }

  public static boolean copyFolderSh(String oldPath, String newPath) {
    Log.i("TaskUtil", "start copyFolderSh : " + oldPath + " ; " + newPath);
    try {
      // 验证输入路径合法性
      if (oldPath == null || newPath == null || oldPath.isEmpty() || newPath.isEmpty()) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Invalid path. oldPath: " + oldPath + ", newPath: " + newPath, null);
        return false;
      }

      // 使用 File API 确保路径处理正确
      File src = new File(oldPath);
      File dst = new File(newPath);

      if (!src.exists()) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Source path does not exist: " + oldPath,null);
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
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Command execution failed. Result: " + result, null);
        return false;
      }

      Log.i("TaskUtil", "Command executed successfully: " + result);
      return true;
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error occurred during copyFolderSh operation", e);
      return false;
    }
  }

  /**
   *在给定的目标目录中清除特定的文件和目录。
   *子目录未命名为“缓存”，并且删除了大于3 MB的文件。
   *记录保留的文件和目录的路径。
   *
   * @param DST将处理其文件和子目录的目标目录
   * @return true如果目的目录存在并且已成功处理，则为false否则
   */
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
      return true;
    }
    return false;
  }

  public static void delFileSh(String path) {
    Log.i("TaskUtil", "start delFileSh : " + path);

    // 1. 参数校验
    if (path == null || path.isEmpty()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR,"TaskUtil", "Invalid or empty path provided.", null);
      return;
    }
    File file = new File(path);
    if (!file.exists()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR,"TaskUtil","File does not exist: " + path, null);
      return;
    }

    // 3. 执行 Shell 命令
    try {
      String cmd = "rm -rf " + path;
      Log.i("TaskUtil", "Attempting to delete file using Shell command.");
      ShellUtils.execRootCmd(cmd);
      Log.i("TaskUtil", "File deletion successful for path: " + path);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error occurred during delFileSh operation", e);
    }
  }

  private static void delFile(File file) {
    if (file == null || !file.exists()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "File does not exist or is null.", null);
      return;
    }

    try {
      String cmd = "rm -rf " + file.getAbsolutePath();
      Log.i("TaskUtil", "Deleting file: " + file.getName());
      ShellUtils.execRootCmd(cmd);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error occurred during delFile operation", e);
      throw new RuntimeException("File deletion failed", e);
    }
  }

  private static void compressToZip(File src, File dst) throws IOException {
    Log.d("TaskUtil", "Starting to compress single file...");

    if (!src.exists() || !src.isFile()) {
      throw new IOException("Source file does not exist or is not a valid file: " + src.getAbsolutePath());
    }
    if (dst.exists()) {
      throw new IOException("Destination ZIP file already exists: " + dst.getAbsolutePath());
    }
    if (!dst.getParentFile().canWrite()) {
      throw new IOException("Parent directory of destination file is not writable: " + dst.getAbsolutePath());
    }

    try (FileOutputStream fos = new FileOutputStream(dst);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        FileInputStream fis = new FileInputStream(src)) {

      String zipEntryName = src.getName();
      zipOut.putNextEntry(new ZipEntry(zipEntryName));

      Log.d("TaskUtil", "Adding file to ZIP: " + zipEntryName);

      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = fis.read(buffer)) >= 0) {
        zipOut.write(buffer, 0, bytesRead);
      }

      zipOut.closeEntry();
      Log.i("TaskUtil", "File successfully compressed to ZIP: " + dst.getName());
    } catch (IOException e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error during file compression", e);
      throw e;
    }
  }

  private static void addDirToZip(File src, String parentPath, ZipOutputStream zipOut) throws IOException {
    File[] files = src.listFiles();
    if (files != null) {
      for (File file : files) {
        String zipEntryName = parentPath + file.getName();
        if (file.isDirectory()) {
          addDirToZip(file, zipEntryName + "/", zipOut);
        } else {
          try (FileInputStream fis = new FileInputStream(file)) {
            zipOut.putNextEntry(new ZipEntry(zipEntryName));
            byte[] buffer = new byte[4096]; // 默认 Buffer 大小
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) >= 0) {
              zipOut.write(buffer, 0, bytesRead);
            }
            zipOut.closeEntry();
          }
        }
      }
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
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Error in zipSh", e);
    }
  }

  public static void unZip(File destFile, File zipFile) {
    Log.d("TaskUtil", "unZip method called with parameters:");
    Log.d("TaskUtil", "Destination file: " + (destFile != null ? destFile.getAbsolutePath() : "null"));
    Log.d("TaskUtil", "ZIP file: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));

    try {
      // 校验输入参数
      if (destFile == null || zipFile == null) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Destination file or ZIP file is null.", null);
        throw new IllegalArgumentException("Destination file or ZIP file cannot be null.");
      }

      if (!zipFile.exists() || !zipFile.isFile()) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Invalid ZIP file: " + zipFile.getAbsolutePath(), null);
        throw new IllegalArgumentException("Invalid ZIP file: " + zipFile.getAbsolutePath());
      }

      if (destFile.exists()) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil", "Destination file already exists: " + destFile.getAbsolutePath(), null);
        throw new IllegalArgumentException("Destination file already exists and cannot be overwritten: " + destFile.getAbsolutePath());
      }

      File parentDir = destFile.getParentFile();
      if (!parentDir.exists()) {
        boolean created = parentDir.mkdirs();
        Log.d("TaskUtil", "Parent directory created: " + created + ", Path: " + parentDir.getAbsolutePath());
        if (!created) {
          throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
        }
      }

      // 使用 Java 自带的 ZipInputStream 解压文件
      try (FileInputStream fis = new FileInputStream(zipFile);
          java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(fis);
          FileOutputStream fos = new FileOutputStream(destFile)) {

        Log.d("TaskUtil", "ZipInputStream opened for ZIP file: " + zipFile.getAbsolutePath());

        java.util.zip.ZipEntry zipEntry;
        int extractedFiles = 0;
        byte[] buffer = new byte[4096];
        int length;

        while ((zipEntry = zis.getNextEntry()) != null) {
          if (extractedFiles > 0) {
            throw new IOException("ZIP file contains more than one entry, expected exactly one: " + zipFile.getAbsolutePath());
          }

          Log.d("TaskUtil", "Processing single entry: " + zipEntry.getName());

          if (zipEntry.isDirectory()) {
            LogFileUtil.logAndWrite(android.util.Log.ERROR, "TaskUtil",
                "ZIP file entry is a directory but only a single file can be extracted: " + zipEntry.getName(), null);
            throw new IOException("ZIP file entry is a directory: " + zipEntry.getName());
          }

          while ((length = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
          }
          extractedFiles++;
          zis.closeEntry();
        }

        if (extractedFiles == 0) {
          throw new IOException("ZIP file does not contain entries to extract: " + zipFile.getAbsolutePath());
        }

        Log.i("TaskUtil", "Unzip successful. Extracted file to: " + destFile.getAbsolutePath());
      }
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "TaskUtil", "Error in unZip method", e);
    }
  }

  public static void unzipSh(File destinationDir, File zipFile) {
    try {
      if (destinationDir == null || zipFile == null) {
        throw new IllegalArgumentException("Destination directory or ZIP file cannot be null.");
      }

      if (!zipFile.exists() || !zipFile.isFile()) {
        throw new IllegalArgumentException("Invalid ZIP file: " + zipFile.getAbsolutePath());
      }

      if (!destinationDir.exists()) {
        boolean mkdirs = destinationDir.mkdirs();
        if (!mkdirs) {
          throw new IOException("Failed to create destination directory: " + destinationDir.getAbsolutePath());
        }
      }

      String destPath = destinationDir.getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");
      String zipFilePath = zipFile.getAbsolutePath().replace(" ", "\\ ").replace("\"", "\\\"");

      // 构造解压命令
      String cmd = "unzip -o \"" + zipFilePath + "\" -d \"" + destPath + "\"";
      Log.i("TaskUtil", "unzipSh-> cmd: " + cmd.replace(zipFilePath, "[ZIP_FILE_PATH]").replace(destPath, "[DESTINATION_PATH]"));

      // 执行命令
      String result = ShellUtils.execRootCmdAndGetResult(cmd);

      // 检查返回结果
      if (result == null || result.contains("error")) {
        LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Shell command execution failed: " + result,null);
        throw new IOException("Shell command failed. Result: " + result);
      }

      Log.i("TaskUtil", "Unzip successful. Extracted to: " + destinationDir.getAbsolutePath());
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Error in unzipSh", e);
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
      LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "File upload failed", e);
      throw e;
    }
  }

  public static File downloadCodeFile(String fileName, File filesLocationDir) {
    String baseUrl = BASE_URL + "/download_code_file";
    String fullUrl = baseUrl + "?file_name=" + fileName;

    Log.d("TaskUtil", "Download URL constructed: " + fullUrl); // 添加日志记录URL

    Request request = new Request.Builder()
        .url(fullUrl)
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      Log.d("TaskUtil", "HTTP request executed. Response code: " + response.code()); // 记录响应代码

      if (response.isSuccessful() && response.body() != null) {
        Log.d("TaskUtil", "Response is successful. Preparing to save file."); // 记录成功响应

        // 检查目录是否存在
        if (!filesLocationDir.exists()) {
          boolean dirCreated = filesLocationDir.mkdirs();
          Log.d("TaskUtil", "Directory created: " + filesLocationDir.getAbsolutePath() + " - " + dirCreated);
        }

        File saveFile = new File(filesLocationDir, fileName);
        Log.d("TaskUtil", "Target file path: " + saveFile.getAbsolutePath());

        try (InputStream is = response.body().byteStream();
            OutputStream os = new BufferedOutputStream(new FileOutputStream(saveFile))) {

          Log.d("TaskUtil", "Starting to write file...");
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
          }

          Log.i("TaskUtil", "File saved successfully to: " + saveFile.getAbsolutePath());
        }
        return saveFile;
      } else {
        Log.w("TaskUtil", "Download failed. HTTP code: " + response.code() + ", Message: " + response.message());

        // 如果响应体不为空则记录内容
        if (response.body() != null) {
          try {
            String responseBody = response.body().string();
            LogFileUtil.logAndWrite(Log.INFO,"TaskUtil", "Response body: " + responseBody,null);
          } catch (IOException e) {
            LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Failed to read response body for logging", e);
          }
        } else {
          LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Response body is null", null);
        }
        return null;
      }
    } catch (IOException e) {
      LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Error during file download", e);
      return null;
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

  public static Map<String, String> getPackageInfo(String androidId) {
    Log.i("TaskUtil", "getPackageInfo called with androidId: " + androidId);

    try {
      // 构造完整的URL
      HttpUrl url = HttpUrl.parse(BASE_URL + "/get_package_info")
          .newBuilder()
          .addQueryParameter("androidId", androidId)
          .build();

      Log.d("TaskUtil", "Constructed URL: " + url.toString());

      // 创建 HTTP 请求
      Request request = new Request.Builder()
          .url(url)
          .get()
          .build();

      // 同步执行网络请求
      try (Response response = okHttpClient.newCall(request).execute()) {
        // 检查响应是否成功
        if (!response.isSuccessful()) {
          String errorMessage = "Unexpected response: Code=" + response.code() +
              ", Message=" + response.message() +
              ", URL=" + url.toString();
          LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", errorMessage, null);
          throw new IOException(errorMessage);
        }

        // 解析响应内容
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          String responseString = responseBody.string();
          // 定义Gson对象
          Gson gson = new Gson();

          // 指定解析的泛型类型为 Map<String, List<String>>
          Type type = new TypeToken<Map<String, String>>() {
          }.getType();

          // 将JSON字符串转换为 Map
          Map<String, String> resultMap = gson.fromJson(responseString, type);

          // 根据androidId获取对应的文件列表
          if (resultMap != null) {
            return resultMap;
          } else {
            Log.w("TaskUtil", "Android ID not found in response.");
            return null;
          }
        } else {
          String errorMessage = "Response body is null for URL=" + url.toString();
          LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", errorMessage, null);
          throw new IOException(errorMessage);
        }
      }
    } catch (IOException e) {
      LogFileUtil.logAndWrite(Log.ERROR,"TaskUtil", "Error during getPackageInfo request", e);
    }

    return null; // 如果出错，返回null
  }

  public static String execQueryTask(String androidId,String taskId) {
    return getDeviceInfoSync(androidId,taskId);
  }

  public static void execSaveTask(Context context, String androidId, String taskId,String packName) {
    if (context == null) {
      throw new IllegalArgumentException("Context or Package name cannot be null or empty");
    }

    if (androidId == null || androidId.isEmpty()) {
      System.err.println("ANDROID_ID is null or empty");
      return;
    }

    try {
      postDeviceInfo(androidId, taskId,packName);
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

