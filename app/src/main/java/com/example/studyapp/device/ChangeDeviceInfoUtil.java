package com.example.studyapp.device;

import static com.example.studyapp.autoJS.AutoJsUtil.isAppInstalled;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.studyapp.task.AfInfo;
import com.example.studyapp.task.BigoInfo;
import com.example.studyapp.task.DeviceInfo;
import com.example.studyapp.task.TaskUtil;
import com.example.studyapp.utils.HttpUtil;
import com.example.studyapp.utils.ShellUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

public class ChangeDeviceInfoUtil {


  private static JSONObject bigoDeviceObject;

  private static JSONObject afDeviceObject;

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
    Log.d("TaskUtil", "initialize method called with parameters:");
    Log.d("TaskUtil", "Country: " + country + ", Tag: " + tag + ", Android ID: " + androidId);
    Log.d("TaskUtil", "Context instance: " + (context != null ? context.getClass().getSimpleName() : "null"));

    executorService.submit(() -> {
      try {
        Log.d("TaskUtil", "Starting network requests...");

        // 发起网络请求
        String bigoJson = HttpUtil.requestGet(buildBigoUrl(country, tag));
        Log.d("TaskUtil", "Received bigoJson: " + bigoJson);

        String afJson = HttpUtil.requestGet(buildAfUrl(country, tag));
        Log.d("TaskUtil", "Received afJson: " + afJson);

        String response = executeQuerySafely(androidId);
        Log.d("TaskUtil", "Response from executeQuerySafely: " + response);

        // 解析 JSON
        if (response != null && !response.isBlank() && !response.equals("{}\n")) {
          Log.d("TaskUtil", "Parsing existing response JSON...");
          JSONObject responseJson = new JSONObject(response);
          bigoDeviceObject = responseJson.optJSONObject("bigoDeviceObject");
          afDeviceObject = responseJson.optJSONObject("afDeviceObject");
          Log.d("TaskUtil", "Parsed bigoDeviceObject: " + bigoDeviceObject);
          Log.d("TaskUtil", "Parsed afDeviceObject: " + afDeviceObject);
        } else {
          Log.d("TaskUtil", "Fallback to parsing bigoJson and afJson...");
          bigoDeviceObject = new JSONObject(bigoJson).optJSONObject("device");
          afDeviceObject = new JSONObject(afJson).optJSONObject("device");
          Log.d("TaskUtil", "Fallback bigoDeviceObject: " + bigoDeviceObject);
          Log.d("TaskUtil", "Fallback afDeviceObject: " + afDeviceObject);
        }

        // 输出结果
        Log.i("TaskUtil", "Final bigoDeviceObject: " + bigoDeviceObject);
        Log.i("TaskUtil", "Final afDeviceObject: " + afDeviceObject);

        // 获取包信息
        Log.d("TaskUtil", "Fetching package info...");
        Map<String, String> packageInfo = TaskUtil.getPackageInfo(androidId);
        Log.d("TaskUtil", "Package info retrieved: " + packageInfo);

        // 遍历包信息并执行逻辑
        if (packageInfo != null) {
          for (String packAgeName : packageInfo.keySet()) {
            Log.d("TaskUtil", "Processing package: " + packAgeName);
            if (isAppInstalled(packAgeName)) {
              Log.d("TaskUtil", "Package installed: " + packAgeName);

              File filesDir = new File(context.getExternalFilesDir(null).getAbsolutePath());
              Log.d("TaskUtil", "Files directory: " + filesDir.getAbsolutePath());

              File file = TaskUtil.downloadCodeFile(packageInfo.get(packAgeName), filesDir);
              if (file != null && file.exists()) {
                Log.d("TaskUtil", "File downloaded: " + file.getAbsolutePath());
                File destDir = new File("/storage/emulated/0/Android/data/" + packAgeName);
                Log.d("TaskUtil", "Unzipping to destination: " + destDir.getAbsolutePath());

                TaskUtil.unZip(destDir, file);
                Log.d("TaskUtil", "Unzip completed. Deleting file: " + file.getAbsolutePath());

                TaskUtil.delFileSh(file.getAbsolutePath());
                Log.d("TaskUtil", "Temporary file deleted: " + file.getAbsolutePath());
              } else {
                Log.w("TaskUtil", "File download failed or file does not exist for package: " + packAgeName);
              }
            } else {
              Log.w("TaskUtil", "Package not installed: " + packAgeName);
            }
          }
        }
      } catch (IOException | JSONException e) {
        Log.e("TaskUtil", "Error occurred during initialization", e);
      } catch (Exception e) {
        Log.e("TaskUtil", "Unexpected error occurred", e);
      }
    });
  }

  // 辅助方法：执行网络请求
  private static String fetchJson(String url) throws IOException {
    return HttpUtil.requestGet(url);
  }

  // 辅助方法：执行任务
  private static String executeQuerySafely(String androidId) {
    return TaskUtil.execQueryTask(androidId);
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
        Log.e("ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
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
          Log.e("ChangeDeviceInfoUtil", "Root access is required to execute system property changes");
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
        Log.e("ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
        throw new RuntimeException("Error occurred in changeDeviceInfo", e);
      }
    }
  }

  private static void callVCloudSettings_put(String key, String value, Context context) {
    if (context == null) {
      throw new IllegalArgumentException("Context cannot be null");
    }
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Key cannot be null or empty");
    }
    if (value == null) {
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
      Log.w("Reflection Error", "Class not found: android.provider.VCloudSettings$Global. This may not be supported on this device.");
    } catch (NoSuchMethodException e) {
      Log.w("Reflection Error", "Method putString not available. Ensure your taropt Android version supports it.");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getTargetException();
      if (cause instanceof SecurityException) {
        Log.e("Reflection Error", "Permission denied. Ensure WRITE_SECURE_SETTINGS permission is granted.", cause);
      } else {
        Log.e("Reflection Error", "InvocationTaroptException during putString invocation", e);
      }
    } catch (Exception e) {
      Log.e("Reflection Error", "Unexpected error during putString invocation: " + e.getMessage());
    }
  }

  public static void resetChangedDeviceInfo(String current_pkg_name, Context context) {
    try {
      Native.setBootId("00000000000000000000000000000000");
    } catch (Exception e) {
      Log.e("resetChangedDeviceInfo", "Failed to set boot ID", e);
    }

    if (!ShellUtils.hasRootAccess()) {
      Log.e("resetChangedDeviceInfo", "Root privileges are required.");
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
