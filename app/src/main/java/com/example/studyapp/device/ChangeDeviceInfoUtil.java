package com.example.studyapp.device;

import static com.example.studyapp.autoJS.AutoJsUtil.isAppInstalled;
import static com.example.studyapp.utils.LogFileUtil.logAndWrite;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.studyapp.task.AfInfo;
import com.example.studyapp.task.BigoInfo;
import com.example.studyapp.task.DeviceInfo;
import com.example.studyapp.task.TaskUtil;
import com.example.studyapp.utils.ApkInstaller;
import com.example.studyapp.utils.HttpUtil;
import com.example.studyapp.utils.LogFileUtil;
import com.example.studyapp.utils.ShellUtils;
import com.example.studyapp.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

  public static boolean getDeviceInfoSync(String taskId, String androidId){
    String response = "";
    try{
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
      LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Invalid task",null);
      return;
    }

    executorService.submit(() -> {
      String response = "";
      try{
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
        LogFileUtil.logAndWrite(android.util.Log.ERROR, LOG_TAG, "Error occurred during query",null);
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

  public static boolean processPackageInfoWithDeviceInfo(String packageName,String zipName, Context context, String androidId, String taskId) {
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
      File file = TaskUtil.downloadCodeFile("FyZqWrStUvOpKlMn_wsj.reader_sp.zip", filesDir);

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
    return TaskUtil.execQueryTask(androidId,taskId);
  }


  public static void changeDeviceInfo(String current_pkg_name, Context context) {

    BigoInfo bigoDevice;
    if (bigoDeviceObject != null) {
      // BIGO
      String cpuClockSpeed = bigoDeviceObject.optString("cpu_clock_speed");
      String gaid = bigoDeviceObject.optString("gaid");
      String userAgent = bigoDeviceObject.optString("User-Agent");
      String osLang = bigoDeviceObject.optString("os_lang");
      String osVer = bigoDeviceObject.optString("os_ver");
      String tz = bigoDeviceObject.optString("tz");
      String systemCountry = bigoDeviceObject.optString("system_country");
      String simCountry = bigoDeviceObject.optString("sim_country");
      long romFreeIn = bigoDeviceObject.optLong("rom_free_in");
      String resolution = bigoDeviceObject.optString("resolution");
      String vendor = bigoDeviceObject.optString("vendor");
      int batteryScale = bigoDeviceObject.optInt("bat_scale");
      // String model = deviceObject.optString("model");
      String net = bigoDeviceObject.optString("net");
      int dpi = bigoDeviceObject.optInt("dpi");
      long romFreeExt = bigoDeviceObject.optLong("rom_free_ext");
      String dpiF = bigoDeviceObject.optString("dpi_f");
      int cpuCoreNum = bigoDeviceObject.optInt("cpu_core_num");

      bigoDevice = new BigoInfo();
      bigoDevice.cpuClockSpeed = cpuClockSpeed;
      bigoDevice.gaid = gaid;
      bigoDevice.userAgent = userAgent;
      bigoDevice.osLang = osLang;
      bigoDevice.osVer = osVer;
      bigoDevice.tz = tz;
      bigoDevice.systemCountry = systemCountry;
      bigoDevice.simCountry = simCountry;
      bigoDevice.romFreeIn = romFreeIn;
      bigoDevice.resolution = resolution;
      bigoDevice.vendor = vendor;
      bigoDevice.batteryScale = batteryScale;
      bigoDevice.net = net;
      bigoDevice.dpi = dpi;
      bigoDevice.romFreeExt = romFreeExt;
      bigoDevice.dpiF = dpiF;
      bigoDevice.cpuCoreNum = cpuCoreNum;
      TaskUtil.setBigoDevice(bigoDevice);
      try {
        callVCloudSettings_put(current_pkg_name + ".system_country", systemCountry, context);
        callVCloudSettings_put(current_pkg_name + ".sim_country", simCountry, context);
        callVCloudSettings_put(current_pkg_name + ".rom_free_in", String.valueOf(romFreeIn), context);
        callVCloudSettings_put(current_pkg_name + ".resolution", resolution, context);
        callVCloudSettings_put(current_pkg_name + ".vendor", vendor, context);
        callVCloudSettings_put(current_pkg_name + ".battery_scale", String.valueOf(batteryScale), context);
        callVCloudSettings_put(current_pkg_name + ".os_lang", osLang, context);
        // callVCloudSettings_put(current_pkg_name + ".model", model, context);
        callVCloudSettings_put(current_pkg_name + ".net", net, context);
        callVCloudSettings_put(current_pkg_name + ".dpi", String.valueOf(dpi), context);
        callVCloudSettings_put(current_pkg_name + ".rom_free_ext", String.valueOf(romFreeExt), context);
        callVCloudSettings_put(current_pkg_name + ".dpi_f", dpiF, context);
        callVCloudSettings_put(current_pkg_name + ".cpu_core_num", String.valueOf(cpuCoreNum), context);
        callVCloudSettings_put(current_pkg_name + ".cpu_clock_speed", cpuClockSpeed, context);
        callVCloudSettings_put(current_pkg_name + "_gaid", gaid, context);
        // **User-Agent**
        callVCloudSettings_put(current_pkg_name + "_user_agent", userAgent, context);
        // **os_lang**系统语言
        callVCloudSettings_put(current_pkg_name + "_os_lang", osLang, context);
        // **os_ver**
        callVCloudSettings_put(current_pkg_name + "_os_ver", osVer, context);
        // **tz** (时区)
        callVCloudSettings_put(current_pkg_name + "_tz", tz, context);
      } catch (Throwable e) {
        logAndWrite(android.util.Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
        throw new RuntimeException("Error occurred in changeDeviceInfo", e);
      }
    }

    DeviceInfo deviceInfo;
    AfInfo afDevice;
    if (afDeviceObject != null) {
      String advertiserId = afDeviceObject.optString(".advertiserId");
      String model = afDeviceObject.optString(".model");
      String brand = afDeviceObject.optString(".brand");
      String androidId = afDeviceObject.optString(".android_id");
      int xPixels = afDeviceObject.optInt(".deviceData.dim.x_px");
      int yPixels = afDeviceObject.optInt(".deviceData.dim.y_px");
      int densityDpi = afDeviceObject.optInt(".deviceData.dim.d_dpi");
      String country = afDeviceObject.optString(".country");
      String batteryLevel = afDeviceObject.optString(".batteryLevel");
      String stackInfo = Thread.currentThread().getStackTrace()[2].toString();
      String product = afDeviceObject.optString(".product");
      String network = afDeviceObject.optString(".network");
      String langCode = afDeviceObject.optString(".lang_code");
      String cpuAbi = afDeviceObject.optString(".deviceData.cpu_abi");
      int yDp = afDeviceObject.optInt(".deviceData.dim.ydp");

      afDevice = new AfInfo();
      afDevice.advertiserId = advertiserId;
      afDevice.model = model;
      afDevice.brand = brand;
      afDevice.androidId = androidId;
      afDevice.xPixels = xPixels;
      afDevice.yPixels = yPixels;
      afDevice.densityDpi = densityDpi;
      afDevice.country = country;
      afDevice.batteryLevel = batteryLevel;
      afDevice.stackInfo = stackInfo;
      afDevice.product = product;
      afDevice.network = network;
      afDevice.langCode = langCode;
      afDevice.cpuAbi = cpuAbi;
      afDevice.yDp = yDp;
      TaskUtil.setAfDevice(afDevice);

      String lang = afDeviceObject.optString(".lang");
      String ro_product_brand = afDeviceObject.optString("ro.product.brand", "");
      String ro_product_model = afDeviceObject.optString("ro.product.model", "");
      String ro_product_manufacturer = afDeviceObject.optString("ro.product.manufacturer", "");
      String ro_product_device = afDeviceObject.optString("ro.product.device", "");
      String ro_product_name = afDeviceObject.optString("ro.product.name", "");
      String ro_build_version_incremental = afDeviceObject.optString("ro.build.version.incremental", "");
      String ro_build_fingerprint = afDeviceObject.optString("ro.build.fingerprint", "");
      String ro_odm_build_fingerprint = afDeviceObject.optString("ro.odm.build.fingerprint", "");
      String ro_product_build_fingerprint = afDeviceObject.optString("ro.product.build.fingerprint", "");
      String ro_system_build_fingerprint = afDeviceObject.optString("ro.system.build.fingerprint", "");
      String ro_system_ext_build_fingerprint = afDeviceObject.optString("ro.system_ext.build.fingerprint", "");
      String ro_vendor_build_fingerprint = afDeviceObject.optString("ro.vendor.build.fingerprint", "");
      String ro_build_platform = afDeviceObject.optString("ro.board.platform", "");
      String persist_sys_cloud_drm_id = afDeviceObject.optString("persist.sys.cloud.drm.id", "");
      int persist_sys_cloud_battery_capacity = afDeviceObject.optInt("persist.sys.cloud.battery.capacity", -1);
      String persist_sys_cloud_gpu_gl_vendor = afDeviceObject.optString("persist.sys.cloud.gpu.gl_vendor", "");
      String persist_sys_cloud_gpu_gl_renderer = afDeviceObject.optString("persist.sys.cloud.gpu.gl_renderer", "");
      String persist_sys_cloud_gpu_gl_version = afDeviceObject.optString("persist.sys.cloud.gpu.gl_version", "");
      String persist_sys_cloud_gpu_egl_vendor = afDeviceObject.optString("persist.sys.cloud.gpu.egl_vendor", "");
      String persist_sys_cloud_gpu_egl_version = afDeviceObject.optString("persist.sys.cloud.gpu.egl_version", "");
      String global_android_id = afDeviceObject.optString(".android_id", "");
      String anticheck_pkgs = afDeviceObject.optString(".anticheck_pkgs", "");
      String pm_list_features = afDeviceObject.optString(".pm_list_features", "");
      String pm_list_libraries = afDeviceObject.optString(".pm_list_libraries", "");
      String system_http_agent = afDeviceObject.optString("system.http.agent", "");
      String webkit_http_agent = afDeviceObject.optString("webkit.http.agent", "");
      String com_fk_tools_pkgInfo = afDeviceObject.optString(".pkg_info", "");
      String appsflyerKey = afDeviceObject.optString(".appsflyerKey", "");
      String appUserId = afDeviceObject.optString(".appUserId", "");
      String disk = afDeviceObject.optString(".disk", "");
      String operator = afDeviceObject.optString(".operator", "");
      String cell_mcc = afDeviceObject.optString(".cell.mcc", "");
      String cell_mnc = afDeviceObject.optString(".cell.mnc", "");
      String date1 = afDeviceObject.optString(".date1", "");
      String date2 = afDeviceObject.optString(".date2", "");
      String bootId = afDeviceObject.optString("BootId", "");

      deviceInfo = new DeviceInfo();
      deviceInfo.lang = lang;
      deviceInfo.roProductBrand = ro_product_brand;
      deviceInfo.roProductModel = ro_product_model;
      deviceInfo.roProductManufacturer = ro_product_manufacturer;
      deviceInfo.roProductDevice = ro_product_device;
      deviceInfo.roProductName = ro_product_name;
      deviceInfo.roBuildVersionIncremental = ro_build_version_incremental;
      deviceInfo.roBuildFingerprint = ro_build_fingerprint;
      deviceInfo.roOdmBuildFingerprint = ro_odm_build_fingerprint;
      deviceInfo.roProductBuildFingerprint = ro_product_build_fingerprint;
      deviceInfo.roSystemBuildFingerprint = ro_system_build_fingerprint;
      deviceInfo.roSystemExtBuildFingerprint = ro_system_ext_build_fingerprint;
      deviceInfo.roVendorBuildFingerprint = ro_vendor_build_fingerprint;
      deviceInfo.roBuildPlatform = ro_build_platform;
      deviceInfo.persistSysCloudDrmId = persist_sys_cloud_drm_id;
      deviceInfo.persistSysCloudBatteryCapacity = persist_sys_cloud_battery_capacity;
      deviceInfo.persistSysCloudGpuGlVendor = persist_sys_cloud_gpu_gl_vendor;
      deviceInfo.persistSysCloudGpuGlRenderer = persist_sys_cloud_gpu_gl_renderer;
      deviceInfo.persistSysCloudGpuGlVersion = persist_sys_cloud_gpu_gl_version;
      deviceInfo.persistSysCloudGpuEglVendor = persist_sys_cloud_gpu_egl_vendor;
      deviceInfo.persistSysCloudGpuEglVersion = persist_sys_cloud_gpu_egl_version;
      TaskUtil.setDeviceInfo(deviceInfo);
      try {
        callVCloudSettings_put(current_pkg_name + ".advertiserId", advertiserId, context);
        callVCloudSettings_put(current_pkg_name + ".model", model, context);
        callVCloudSettings_put(current_pkg_name + ".brand", brand, context);
        callVCloudSettings_put(current_pkg_name + ".android_id", androidId, context);
        callVCloudSettings_put(current_pkg_name + ".lang", lang, context);
        callVCloudSettings_put(current_pkg_name + ".country", country, context);
        callVCloudSettings_put(current_pkg_name + ".batteryLevel", batteryLevel, context);
        callVCloudSettings_put(current_pkg_name + "_screen.optMetrics.stack", stackInfo, context);
        callVCloudSettings_put(current_pkg_name + ".product", product, context);
        callVCloudSettings_put(current_pkg_name + ".network", network, context);
        callVCloudSettings_put(current_pkg_name + ".cpu_abi", cpuAbi, context);
        callVCloudSettings_put(current_pkg_name + ".lang_code", langCode, context);
        // **广告标识符 (advertiserId)** 及 **启用状态**
        boolean isAdIdEnabled = true; // 默认启用广告 ID
        callVCloudSettings_put(current_pkg_name + ".advertiserIdEnabled", String.valueOf(isAdIdEnabled), context);

        JSONObject displayMetrics = new JSONObject();

        displayMetrics.put("widthPixels", xPixels);

        displayMetrics.put("heightPixels", yPixels);
        displayMetrics.put("densityDpi", densityDpi);
        displayMetrics.put("yDp", yDp);
        callVCloudSettings_put("screen.device.displayMetrics", displayMetrics.toString(), context);

        if (!ShellUtils.hasRootAccess()) {
          LogFileUtil.writeLogToFile("ERROR", "ChangeDeviceInfoUtil", "Root access is required to execute system property changes");
        }
        // 设置机型, 直接设置属性
        ShellUtils.execRootCmd("setprop ro.product.brand " + ro_product_brand);
        ShellUtils.execRootCmd("setprop ro.product.model " + ro_product_model);
        ShellUtils.execRootCmd("setprop ro.product.manufacturer " + ro_product_manufacturer);
        ShellUtils.execRootCmd("setprop ro.product.device " + ro_product_device);
        ShellUtils.execRootCmd("setprop ro.product.name " + ro_product_name);
        ShellUtils.execRootCmd("setprop ro.build.version.incremental " + ro_build_version_incremental);
        ShellUtils.execRootCmd("setprop ro.build.fingerprint " + ro_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.odm.build.fingerprint " + ro_odm_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.product.build.fingerprint " + ro_product_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.system.build.fingerprint " + ro_system_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.system_ext.build.fingerprint " + ro_system_ext_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.vendor.build.fingerprint " + ro_vendor_build_fingerprint);
        ShellUtils.execRootCmd("setprop ro.board.platform " + ro_build_platform);

        // Native.setBootId(bootId);
        // 修改drm id
        ShellUtils.execRootCmd("setprop persist.sys.cloud.drm.id " + persist_sys_cloud_drm_id);
        // 电量模拟需要大于1000
        ShellUtils.execRootCmd("setprop persist.sys.cloud.battery.capacity " + persist_sys_cloud_battery_capacity);
        ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_vendor " + persist_sys_cloud_gpu_gl_vendor);
        ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_renderer " + persist_sys_cloud_gpu_gl_renderer);
        // 这个值不能随便改  必须是 OpenGL ES %d.%d 这个格式
        ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_version " + persist_sys_cloud_gpu_gl_version);
        ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_vendor " + persist_sys_cloud_gpu_egl_vendor);
        ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_version " + persist_sys_cloud_gpu_egl_version);
      } catch (Throwable e) {
        logAndWrite(Log.ERROR, "ChangeDeviceInfoUtil", "Error occurred in changeDeviceInfo", e);
        throw new RuntimeException("Error occurred in changeDeviceInfo", e);
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
