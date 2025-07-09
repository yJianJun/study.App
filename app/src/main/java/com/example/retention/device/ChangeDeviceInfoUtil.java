package com.example.retention.device;

import static com.example.retention.autoJS.AutoJsUtil.isAppInstalled;
import static com.example.retention.utils.LogFileUtil.logAndWrite;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.example.retention.device.ArmCloudApiClient.PropertyItem;
import com.example.retention.task.AfInfo;
import com.example.retention.task.BigoInfo;
import com.example.retention.task.DeviceInfo;
import com.example.retention.task.TaskUtil;
import com.example.retention.utils.ApkInstaller;
import com.example.retention.utils.HttpUtil;
import com.example.retention.utils.LogFileUtil;
import com.example.retention.utils.ShellUtils;
import com.example.retention.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

public class ChangeDeviceInfoUtil {


  private static JSONObject bigoDeviceObject;

  private static JSONObject afDeviceObject;

  public static String packageName = "";
  public static String zipName = "";

  public static String buildBigoUrl(String country, int tag) {
    return Uri.parse("http://8.217.137.25/tt/zj/dispatcher!bigo.do")
        .buildUpon()
        .appendQueryParameter("country", country)
        .appendQueryParameter("tag", String.valueOf(tag))
        .toString();
  }

  public static String buildAfUrl(String country, int tag) {
    return Uri.parse("http://8.217.137.25/tt/zj/dispatcher!af.do")
        .buildUpon()
        .appendQueryParameter("country", country)
        .appendQueryParameter("tag", String.valueOf(tag))
        .toString();
  }

  // 创建一个线程池用于执行网络任务
  private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

  public static void initialize(String country, int tag, Context context, String androidId) {
    LogFileUtil.logAndWrite(android.util.Log.DEBUG, LOG_TAG, "Initializing device info...", null);

    executorService.submit(() -> {
      try {
        LogFileUtil.logAndWrite(android.util.Log.DEBUG, LOG_TAG, "Starting network requests...", null);
        String bigoJson = fetchJsonSafely(buildBigoUrl(country, tag), "bigoJson");
        String afJson = fetchJsonSafely(buildAfUrl(country, tag), "afJson");

        fallBackToNetworkData(bigoJson, afJson);

        logDeviceObjects();
        processPackageInfo(TaskUtil.getPackageInfo(androidId), context);

      } catch (IOException | JSONException e) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during initialization", e);
      } catch (Exception e) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during initialization", e);
      }
    });
  }

  public static boolean getDeviceInfoSync(String taskId, String androidId) {
    String response = "";
    try {
      response = executeQuerySafely(androidId, taskId);
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (response == null || response.isBlank()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during query", null);
      return false;
    }
    try {
      synchronized (ChangeDeviceInfoUtil.class) { // 防止并发访问
        parseAndSetDeviceObjects(response);
      }
      return true;
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error parsing JSON", e);
      return false;
    }
  }

  public static void getDeviceInfo(String taskId, String androidId) {
    if (taskId == null || androidId == null || taskId.isBlank() || androidId.isBlank()) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Invalid task", null);
      return;
    }

    executorService.submit(() -> {
      String response = "";
      try {
        response = executeQuerySafely(androidId, taskId);
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (response == null || response.isBlank()) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during query", null);
        return;
      }

      if (isValidResponse(response)) {
        try {
          synchronized (ChangeDeviceInfoUtil.class) { // 防止并发访问
            parseAndSetDeviceObjects(response);
          }
        } catch (JSONException e) {
          LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error parsing JSON", e);
        }
      } else {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during query", null);
      }
    });
  }

  private static String fetchJsonSafely(String url, String logKey) throws IOException {
    String json = null;
    try {
      json = HttpUtil.requestGet(url);
      if (json != null && !json.isEmpty()) {
        LogFileUtil.logAndWrite(android.util.Log.DEBUG, LOG_TAG, "Received " + logKey + ": " + json, null);
        return json;
      } else {
        LogFileUtil.logAndWrite(android.util.Log.WARN, LOG_TAG, "Empty or null response for: " + logKey + ", retrying...", null);
      }
    } catch (IOException e) {
      LogFileUtil.logAndWrite(android.util.Log.WARN, LOG_TAG, "Error fetching " + logKey + ": " + e.getMessage() + ", retrying...", e);
    }

    // Retry once if the initial attempt failed
    json = HttpUtil.requestGet(url);
    if (json != null && !json.isEmpty()) {
      LogFileUtil.logAndWrite(android.util.Log.DEBUG, LOG_TAG, "Retry success for " + logKey + ": " + json, null);
      return json;
    } else {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Retry failed for " + logKey + ", response is still null or empty.", null);
      throw new IOException("Failed to fetch valid JSON for " + logKey);
    }
  }

  private static boolean isValidResponse(String response) {
    return response != null && !response.isBlank() && !response.equals("{}\n")
        && !response.equals("{\"afDeviceObject\": null, \"bigoDeviceObject\": null, \"other\": null}")
        && response.trim().startsWith("{");
  }

  private static void parseAndSetDeviceObjects(String response) throws JSONException {
    String cleanJson = response.trim();
    if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
      cleanJson = cleanJson.substring(1, cleanJson.length() - 1).replace("\\\"", "\"");
    }
    JSONObject responseJson = new JSONObject(cleanJson);
    bigoDeviceObject = responseJson.optJSONObject("bigoDeviceObject");
    afDeviceObject = responseJson.optJSONObject("afDeviceObject");
    packageName = responseJson.optString("package_name");
    zipName = responseJson.optString("file_name");
  }

  private static void fallBackToNetworkData(String bigoJson, String afJson) throws JSONException {
    bigoDeviceObject = new JSONObject(bigoJson).optJSONObject("device");
    afDeviceObject = new JSONObject(afJson).optJSONObject("device");
  }

  private static void logDeviceObjects() {
    LogFileUtil.logAndWrite(android.util.Log.INFO, LOG_TAG, "Final bigoDeviceObject: " + bigoDeviceObject, null);
    LogFileUtil.logAndWrite(android.util.Log.INFO, LOG_TAG, "Final DeviceInfo: " + afDeviceObject, null);
  }

  public static void processPackageInfo(Map<String, String> packageInfo, Context context) {
    if (packageInfo != null) {
      for (Map.Entry<String, String> entry : packageInfo.entrySet()) {
        String packageName = entry.getKey();
        if (!isAppInstalled(packageName)) {
          processPackage(packageName, entry.getValue(), context);
        } else {
          LogFileUtil.logAndWrite(android.util.Log.WARN, LOG_TAG, "Package not installed: " + packageName, null);
        }
      }
    }
  }

  public static boolean processPackageInfoWithDeviceInfo(String packageName, String zipName, Context context, String androidId, String taskId) {
    if (!isAppInstalled(packageName)) {
      return processPackage(packageName, zipName, context);
//      TaskUtil.postDeviceInfo(androidId, taskId, packageName);
    } else {
      LogFileUtil.logAndWrite(android.util.Log.WARN, LOG_TAG, "Package not installed: " + packageName, null);
      return false;
    }
  }

  private static boolean processPackage(String packageName, String zipName, Context context) {
    try {
      File filesDir = new File(context.getExternalFilesDir(null).getAbsolutePath());
      File file = TaskUtil.downloadCodeFile(zipName, filesDir);

      if (file != null && file.exists()) {
        File destFile = new File(context.getCacheDir(), packageName);
        if (destFile.exists()) {
          TaskUtil.delFileSh(destFile.getAbsolutePath());
        }
        ZipUtils.unzip(file.getAbsolutePath(), destFile.getAbsolutePath());
        if (destFile.exists()) {
          installApk(destFile.getAbsolutePath());
        }

        TaskUtil.delFileSh(destFile.getAbsolutePath());
        TaskUtil.delFileSh(file.getAbsolutePath());
        LogFileUtil.logAndWrite(Log.DEBUG, LOG_TAG, "Processed package: " + packageName, null);
        return true;
      } else {
        LogFileUtil.logAndWrite(android.util.Log.WARN, LOG_TAG, "File download failed for package: " + packageName, null);
        return false;
      }
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error processing package: " + packageName, e);
      return false;
    }
  }

  public static boolean installApk(String apkFilePath) {
    // 检查文件路径
    if (apkFilePath == null || apkFilePath.trim().isEmpty()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Invalid APK file path", null);
      return false;
    }

    // 确保文件存在
    File apkFile = new File(apkFilePath);
    if (!apkFile.exists()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "APK file not found: " + apkFilePath, null);
      return false;
    }

    boolean result = ApkInstaller.batchInstallWithRoot(apkFilePath);
    if (result) {
      Log.d("ShellUtils", "APK installed successfully!");
      return true;
    } else {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Failed to install APK. Result: " + result, null);
      return false;
    }
  }


  private static final String LOG_TAG = "TaskUtil";
  private static final String INIT_LOG_TEMPLATE = "initialize method called with parameters: Country: %s, Tag: %d, Android ID: %s";
  private static final String CONTEXT_LOG_TEMPLATE = "Context instance: %s";


  // 辅助方法：执行网络请求
  private static String fetchJson(String url) throws IOException {
    return HttpUtil.requestGet(url);
  }

  // 辅助方法：执行任务
  private static String executeQuerySafely(String androidId, String taskId) {
    return TaskUtil.execQueryTask(androidId, taskId);
  }


  public static void changeDeviceInfo(String current_pkg_name, Context context, ArmCloudApiClient client) {

    final String B_PREFIX = current_pkg_name + ".";
    final String U_PREFIX = current_pkg_name + "_";

    if (bigoDeviceObject != null) {
      // BIGO
      BigoInfo bigoDevice = new BigoInfo();
      bigoDevice.cpuClockSpeed = bigoDeviceObject.optString("cpu_clock_speed");
      bigoDevice.gaid = bigoDeviceObject.optString("gaid");
      bigoDevice.userAgent = bigoDeviceObject.optString("User-Agent");
      bigoDevice.osLang = bigoDeviceObject.optString("os_lang");
      bigoDevice.osVer = bigoDeviceObject.optString("os_ver");
      bigoDevice.tz = bigoDeviceObject.optString("tz");
      bigoDevice.systemCountry = bigoDeviceObject.optString("system_country");
      bigoDevice.simCountry = bigoDeviceObject.optString("sim_country");
      bigoDevice.romFreeIn = bigoDeviceObject.optLong("rom_free_in");
      bigoDevice.resolution = bigoDeviceObject.optString("resolution");
      bigoDevice.vendor = bigoDeviceObject.optString("vendor");
      bigoDevice.batteryScale = bigoDeviceObject.optInt("bat_scale");
      bigoDevice.net = bigoDeviceObject.optString("net");
      bigoDevice.dpi = bigoDeviceObject.optInt("dpi");
      bigoDevice.romFreeExt = bigoDeviceObject.optLong("rom_free_ext");
      bigoDevice.dpiF = bigoDeviceObject.optString("dpi_f");
      bigoDevice.cpuCoreNum = bigoDeviceObject.optInt("cpu_core_num");

      TaskUtil.setBigoDevice(bigoDevice);

      try {
        Map<String, Object> bigoMap = new HashMap<>();
        bigoMap.put(B_PREFIX + "system_country", bigoDevice.systemCountry);
        bigoMap.put(B_PREFIX + "sim_country", bigoDevice.simCountry);
        bigoMap.put(B_PREFIX + "rom_free_in", String.valueOf(bigoDevice.romFreeIn));
        bigoMap.put(B_PREFIX + "resolution", bigoDevice.resolution);
        bigoMap.put(B_PREFIX + "vendor", bigoDevice.vendor);
        bigoMap.put(B_PREFIX + "battery_scale", String.valueOf(bigoDevice.batteryScale));
        bigoMap.put(B_PREFIX + "os_lang", bigoDevice.osLang);
        bigoMap.put(B_PREFIX + "net", bigoDevice.net);
        bigoMap.put(B_PREFIX + "dpi", String.valueOf(bigoDevice.dpi));
        bigoMap.put(B_PREFIX + "rom_free_ext", String.valueOf(bigoDevice.romFreeExt));
        bigoMap.put(B_PREFIX + "dpi_f", bigoDevice.dpiF);
        bigoMap.put(B_PREFIX + "cpu_core_num", String.valueOf(bigoDevice.cpuCoreNum));
        bigoMap.put(B_PREFIX + "cpu_clock_speed", bigoDevice.cpuClockSpeed);
        bigoMap.put(current_pkg_name + "_gaid", bigoDevice.gaid);
        bigoMap.put(current_pkg_name + "_user_agent", bigoDevice.userAgent);
        bigoMap.put(current_pkg_name + "_os_lang", bigoDevice.osLang);
        bigoMap.put(current_pkg_name + "_os_ver", bigoDevice.osVer);
        bigoMap.put(current_pkg_name + "_tz", bigoDevice.tz);

        for (Map.Entry<String, Object> entry : bigoMap.entrySet()) {
          callVCloudSettings_put(entry.getKey(), entry.getValue().toString(), context);
        }

      } catch (Throwable e) {
        logAndWrite(android.util.Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
        throw e; // 保留原始异常类型
      }
    } else {
      Log.w("ChangeDeviceInfoUtil", "bigoDeviceObject is null, skipping BIGO settings.");
    }

    if (afDeviceObject != null) {
      AfInfo afDevice = new AfInfo();
      afDevice.advertiserId = afDeviceObject.optString(".advertiserId");
      afDevice.model = afDeviceObject.optString(".model");
      afDevice.brand = afDeviceObject.optString(".brand");
      afDevice.androidId = afDeviceObject.optString(".android_id");
      afDevice.xPixels = afDeviceObject.optInt(".deviceData.dim.x_px");
      afDevice.yPixels = afDeviceObject.optInt(".deviceData.dim.y_px");
      afDevice.densityDpi = afDeviceObject.optInt(".deviceData.dim.d_dpi");
      afDevice.country = afDeviceObject.optString(".country");
      afDevice.batteryLevel = afDeviceObject.optString(".batteryLevel");
      afDevice.stackInfo = Thread.currentThread().getStackTrace()[2].toString();
      afDevice.product = afDeviceObject.optString(".product");
      afDevice.network = afDeviceObject.optString(".network");
      afDevice.langCode = afDeviceObject.optString(".lang_code");
      afDevice.cpuAbi = afDeviceObject.optString(".deviceData.cpu_abi");
      afDevice.yDp = afDeviceObject.optInt(".deviceData.dim.ydp");

      TaskUtil.setAfDevice(afDevice);

      DeviceInfo deviceInfo = new DeviceInfo();
      deviceInfo.lang = afDeviceObject.optString(".lang");
      deviceInfo.roProductBrand = afDeviceObject.optString("ro.product.brand", "");
      deviceInfo.roProductModel = afDeviceObject.optString("ro.product.model", "");
      deviceInfo.roProductManufacturer = afDeviceObject.optString("ro.product.manufacturer", "");
      deviceInfo.roProductDevice = afDeviceObject.optString("ro.product.device", "");
      deviceInfo.roProductName = afDeviceObject.optString("ro.product.name", "");
      deviceInfo.roBuildVersionIncremental = afDeviceObject.optString("ro.build.version.incremental", "");
      deviceInfo.roBuildFingerprint = afDeviceObject.optString("ro.build.fingerprint", "");
      deviceInfo.roOdmBuildFingerprint = afDeviceObject.optString("ro.odm.build.fingerprint", "");
      deviceInfo.roProductBuildFingerprint = afDeviceObject.optString("ro.product.build.fingerprint", "");
      deviceInfo.roSystemBuildFingerprint = afDeviceObject.optString("ro.system.build.fingerprint", "");
      deviceInfo.roSystemExtBuildFingerprint = afDeviceObject.optString("ro.system_ext.build.fingerprint", "");
      deviceInfo.roVendorBuildFingerprint = afDeviceObject.optString("ro.vendor.build.fingerprint", "");
      deviceInfo.roBuildPlatform = afDeviceObject.optString("ro.board.platform", "");
      deviceInfo.persistSysCloudDrmId = afDeviceObject.optString("persist.sys.cloud.drm.id", "");
      deviceInfo.persistSysCloudBatteryCapacity = afDeviceObject.optInt("persist.sys.cloud.battery.capacity", -1);
      deviceInfo.persistSysCloudGpuGlVendor = afDeviceObject.optString("persist.sys.cloud.gpu.gl_vendor", "");
      deviceInfo.persistSysCloudGpuGlRenderer = afDeviceObject.optString("persist.sys.cloud.gpu.gl_renderer", "");
      deviceInfo.persistSysCloudGpuGlVersion = afDeviceObject.optString("persist.sys.cloud.gpu.gl_version", "");
      deviceInfo.persistSysCloudGpuEglVendor = afDeviceObject.optString("persist.sys.cloud.gpu.egl_vendor", "");
      deviceInfo.persistSysCloudGpuEglVersion = afDeviceObject.optString("persist.sys.cloud.gpu.egl_version", "");

      TaskUtil.setDeviceInfo(deviceInfo);

      try {
        Map<String, Object> afMap = new HashMap<>();
        afMap.put(B_PREFIX + "advertiserId", afDevice.advertiserId);
        afMap.put(B_PREFIX + "model", afDevice.model);
        afMap.put(B_PREFIX + "brand", afDevice.brand);
        afMap.put(B_PREFIX + "android_id", afDevice.androidId);
        afMap.put(B_PREFIX + "lang", afDevice.langCode);
        afMap.put(B_PREFIX + "country", afDevice.country);
        afMap.put(B_PREFIX + "batteryLevel", afDevice.batteryLevel);
        afMap.put(B_PREFIX + "screen.optMetrics.stack", afDevice.stackInfo);
        afMap.put(B_PREFIX + "product", afDevice.product);
        afMap.put(B_PREFIX + "network", afDevice.network);
        afMap.put(B_PREFIX + "cpu_abi", afDevice.cpuAbi);
        afMap.put(B_PREFIX + "lang_code", afDevice.langCode);

        for (Map.Entry<String, Object> entry : afMap.entrySet()) {
          callVCloudSettings_put(entry.getKey(), entry.getValue().toString(), context);
        }

        callVCloudSettings_put(current_pkg_name + ".advertiserIdEnabled", "true", context);

        JSONObject displayMetrics = new JSONObject();
        displayMetrics.put("widthPixels", afDevice.xPixels);
        displayMetrics.put("heightPixels", afDevice.yPixels);
        displayMetrics.put("densityDpi", afDevice.densityDpi);
        displayMetrics.put("yDp", afDevice.yDp);
        callVCloudSettings_put("screen.device.displayMetrics", displayMetrics.toString(), context);

        // 使用 ArmCloud API 设置系统属性
        List<PropertyItem> systemProps = new ArrayList<>();

        addProperty(systemProps, "ro.product.brand", deviceInfo.roProductBrand);
        addProperty(systemProps, "ro.product.model", deviceInfo.roProductModel);
        addProperty(systemProps, "ro.product.manufacturer", deviceInfo.roProductManufacturer);
        addProperty(systemProps, "ro.product.device", deviceInfo.roProductDevice);
        addProperty(systemProps, "ro.product.name", deviceInfo.roProductName);
        addProperty(systemProps, "ro.build.version.incremental", deviceInfo.roBuildVersionIncremental);
        addProperty(systemProps, "ro.build.fingerprint", deviceInfo.roBuildFingerprint);
        addProperty(systemProps, "ro.odm.build.fingerprint", deviceInfo.roOdmBuildFingerprint);
        addProperty(systemProps, "ro.product.build.fingerprint", deviceInfo.roProductBuildFingerprint);
        addProperty(systemProps, "ro.system.build.fingerprint", deviceInfo.roSystemBuildFingerprint);
        addProperty(systemProps, "ro.system_ext.build.fingerprint", deviceInfo.roSystemExtBuildFingerprint);
        addProperty(systemProps, "ro.vendor.build.fingerprint", deviceInfo.roVendorBuildFingerprint);
        addProperty(systemProps, "ro.board.platform", deviceInfo.roBuildPlatform);

        // 调用接口更新实例属性
        try {
          String[] padCodes = client.getDeviceCodes(1, 100, null, null, null, null, null, null, null, null);
          String response = client.updateInstanceProperties(
              padCodes,
              null,  // modemPersistProps
              null,  // modemProps
              null,  // systemPersistProps
              systemProps,
              null,  // settingProps
              null   // oaidProps
          );
          Log.d("ChangeDeviceInfoUtil", "updateInstanceProperties response: " + response);
        } catch (IOException e) {
          Log.e("ChangeDeviceInfoUtil", "Failed to update instance properties", e);
        }

      } catch (Throwable e) {
        logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred in changeDeviceInfo", e);
      }
    } else {
      Log.w("ChangeDeviceInfoUtil", "afDeviceObject is null, skipping AF settings.");
    }
  }

  private static void addProperty(List<PropertyItem> list, String name, String value) {
    if (value != null && !value.isEmpty()) {
      list.add(new PropertyItem(name, value));
    }
  }

  private static void execRootCmdIfNotEmpty(String cmd, String value) {
    if (value != null && !value.isEmpty()) {
      String result = ShellUtils.execRootCmdAndGetResult(cmd + value);
      if (result == null) {
        Log.e("ChangeDeviceInfoUtil", "Failed to execute shell command: " + cmd + value + ", result was empty or null.");
      } else {
        Log.d("ChangeDeviceInfoUtil", "Successfully executed command: " + cmd + value + ", output: " + result);
      }
    }
  }


  private static void callVCloudSettings_put(String key, String value, Context context) {
    if (context == null) {
      logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Context cannot be null", null);
      throw new IllegalArgumentException("Context cannot be null");
    }
    if (key == null || key.isEmpty()) {
      logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Key cannot be null or empty", null);
      throw new IllegalArgumentException("Key cannot be null or empty");
    }
    if (value == null) {
      logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Value cannot be null", null);
      throw new IllegalArgumentException("Value cannot be null");
    }

    try {
      // 获取类对象
      Class<?> clazz = Class.forName("android.provider.VCloudSettings$Global");
      Method putStringMethod = clazz.getDeclaredMethod("putString", ContentResolver.class, String.class, String.class);
      putStringMethod.setAccessible(true);

      // 调用方法
      putStringMethod.invoke(null, context.getContentResolver(), key, value);
      Log.d("Debug", "putString executed successfully.");
    } catch (ClassNotFoundException e) {
      logAndWrite(Log.WARN, "ChangeDeviceInfoUtil", "Class not found: android.provider.VCloudSettings$Global. This may not be supported on this device.", e);
    } catch (NoSuchMethodException e) {
      logAndWrite(Log.WARN, "ChangeDeviceInfoUtil", "Method not found: android.provider.VCloudSettings$Global.putString. This may not be supported on this", e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getTargetException();
      if (cause instanceof SecurityException) {
        logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred in changeDeviceInfo", cause);
      } else {
        logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred in changeDeviceInfo", cause);
      }
    } catch (Exception e) {
      logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Unexpected error during putString invocation", e);
    }
  }

  public static void resetChangedDeviceInfo(String current_pkg_name, Context context) {
    try {
      Native.setBootId("00000000000000000000000000000000");
    } catch (Exception e) {
      logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred in reset", e);
    }

    if (!ShellUtils.hasRootAccess()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Root access is required to execute system property changes", null);
      return;
    }
    ShellUtils.execRootCmd("cmd settings2 delete global global_android_id");
    ShellUtils.execRootCmd("cmd settings2 delete global pm_list_features");
    ShellUtils.execRootCmd("cmd settings2 delete global pm_list_libraries");
    ShellUtils.execRootCmd("cmd settings2 delete global anticheck_pkgs");
    ShellUtils.execRootCmd("cmd settings2 delete global " + current_pkg_name + "_android_id");
    ShellUtils.execRootCmd("cmd settings2 delete global " + current_pkg_name + "_adb_enabled");
    ShellUtils.execRootCmd("cmd settings2 delete global " + current_pkg_name + "_development_settings_enabled");

    ShellUtils.execRootCmd("setprop persist.sys.cloud.drm.id \"\"");

    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_vendor \"\"");
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_renderer \"\"");
    // 这个值不能随便改  必须是 OpenGL ES %d.%d 这个格式
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_version \"\"");

    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_vendor \"\"");
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_version \"\"");

    ShellUtils.execRootCmd("setprop ro.product.brand Vortex");
    ShellUtils.execRootCmd("setprop ro.product.model HD65_Select");
    ShellUtils.execRootCmd("setprop ro.product.manufacturer Vortex");
    ShellUtils.execRootCmd("setprop ro.product.device HD65_Select");
    ShellUtils.execRootCmd("setprop ro.product.name HD65_Select");
    ShellUtils.execRootCmd("setprop ro.build.version.incremental 20240306");
    ShellUtils.execRootCmd("setprop ro.build.fingerprint \"Vortex/HD65_Select/HD65_Select:13/TP1A.220624.014/20240306:user/release-keys\"");
    ShellUtils.execRootCmd("setprop ro.board.platform sm8150p");
  }
}
