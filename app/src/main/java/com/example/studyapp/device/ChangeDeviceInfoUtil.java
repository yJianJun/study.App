package com.example.studyapp.device;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.example.studyapp.utils.HttpUtil;
import com.example.studyapp.utils.ShellUtils;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ChangeDeviceInfoUtil {

  private static final String BIGO_URL = "http://8.217.137.25/tt/zj/dispatcher!bigo.do?country=US&tag=2";
  private static final JSONObject bigoDeviceObject;

  private static final String AF_URL = "http://8.217.137.25/tt/zj/dispatcher!bigo.do?country=US&tag=2";
  private static final JSONObject afDeviceObject;

  static {
    try {
      // 请求接口并获取 JSON
      String bigoJson = HttpUtil.requestGet(BIGO_URL);
      final String afJson = HttpUtil.requestGet(AF_URL);
      // 解析 JSON 字符串
      JSONObject bigoObject = new JSONObject(bigoJson);
      bigoDeviceObject = bigoObject.optJSONObject("device");
      JSONObject afObject = new JSONObject(bigoJson);
      afDeviceObject = bigoObject.optJSONObject("device");
      if (bigoDeviceObject == null) {
        throw new JSONException("Device object is missing in the bigo response JSON");
      }
      if (afDeviceObject == null) {
        throw new JSONException("Device object is missing in the af response JSON");
      }
      Log.d("Debug", "bigoDeviceObject: " + bigoDeviceObject.toString());
      Log.d("Debug", "afDeviceObject: " + afDeviceObject.toString());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load or parse the response JSON", e);
    }
  }


  public static void changeDeviceInfo(String current_pkg_name, Context context) {

    try {
      // 动态读取 JSON 中的值
      String cpuClockSpeed = bigoDeviceObject.getString("cpu_clock_speed");
     // String country = bigoDeviceObject.getString("country");
      String gaid = bigoDeviceObject.getString("gaid");
      String userAgent = bigoDeviceObject.getString("User-Agent");
      String osLang = bigoDeviceObject.getString("os_lang");
      String osVer = bigoDeviceObject.getString("os_ver");
     // String model = bigoDeviceObject.getString("model");
      String tz = bigoDeviceObject.getString("tz");

      // 动态读取 JSON 中的值
      String advertiserId = afDeviceObject.getString(".advertiserId");
      String model = afDeviceObject.getString(".model");
      String brand = afDeviceObject.getString(".brand");
      String androidId = afDeviceObject.getString(".android_id");
      int xPixels = afDeviceObject.optInt(".deviceData.dim.x_px");
      int yPixels = afDeviceObject.optInt(".deviceData.dim.y_px");
      int densityDpi = afDeviceObject.optInt(".deviceData.dim.d_dpi");
      String lang = afDeviceObject.getString(".lang");
      String country = afDeviceObject.getString(".country");
      String batteryLevel = afDeviceObject.getString(".batteryLevel");

      // 替换写死的值为 JSON 动态值
      callVCloudSettings_put(current_pkg_name + ".advertiserId", advertiserId, context);
      callVCloudSettings_put(current_pkg_name + ".model", model, context);
      callVCloudSettings_put(current_pkg_name + ".brand", brand, context);
      callVCloudSettings_put(current_pkg_name + ".android_id", androidId, context);

      JSONObject displayMetrics = new JSONObject();
      displayMetrics.put("widthPixels", xPixels);
      displayMetrics.put("heightPixels", yPixels);
      displayMetrics.put("densityDpi", densityDpi);
      callVCloudSettings_put("screen.getDisplayMetrics", displayMetrics.toString(), context);

      callVCloudSettings_put(current_pkg_name + ".lang", lang, context);
      callVCloudSettings_put(current_pkg_name + ".country", country, context);
      callVCloudSettings_put(current_pkg_name + ".batteryLevel", batteryLevel, context);

      Log.d("ChangeDeviceInfoUtil", "Device info successfully updated.");


      // 指定包名优先级高于全局
      callVCloudSettings_put(current_pkg_name + "_android_id", "my123456", context);
      callVCloudSettings_put(current_pkg_name + "_screen_brightness", "100", context);
      callVCloudSettings_put(current_pkg_name + "_adb_enabled", "1", context);
      callVCloudSettings_put(current_pkg_name + "_development_settings_enabled", "1", context);

      callVCloudSettings_put("pm_list_features", "my_pm_list_features", context);
      callVCloudSettings_put("pm_list_libraries", "my_pm_list_libraries", context);
      callVCloudSettings_put("system.http.agent", "my_system.http.agent", context);
      callVCloudSettings_put("webkit.http.agent", "my_webkit.http.agent", context);

      callVCloudSettings_put("global_android_id", "123456", context);

      callVCloudSettings_put("anticheck_pkgs", current_pkg_name, context);

      JSONObject pkg_info_json = new JSONObject();
      pkg_info_json.put("versionName", "1.0.0");
      pkg_info_json.put("versionCode", 100);
      pkg_info_json.put("firstInstallTime", 1);
      pkg_info_json.put("lastUpdateTime", 1);
      callVCloudSettings_put("com.fk.tools_pkgInfo", pkg_info_json.toString(), context);

      JSONObject tmp_json = new JSONObject();
      tmp_json.put("widthPixels", 1080);
      tmp_json.put("heightPixels", 1920);
      tmp_json.put("densityDpi", 440);
      tmp_json.put("xdpi", 160);
      tmp_json.put("ydpi", 160);
      tmp_json.put("density", 3.0);
      tmp_json.put("scaledDensity", 3.0);
      callVCloudSettings_put("screen.getDisplayMetrics", tmp_json.toString(), context);
      callVCloudSettings_put("screen.getMetrics", tmp_json.toString(), context);
      callVCloudSettings_put("screen.getRealMetrics", tmp_json.toString(), context);
      callVCloudSettings_put(current_pkg_name + "_screen.getDisplayMetrics.stack", ".getDeviceInfo", context);
      String stackInfo = Thread.currentThread().getStackTrace()[2].toString();
      callVCloudSettings_put(current_pkg_name + "_screen.getMetrics.stack", stackInfo, context);
      callVCloudSettings_put(current_pkg_name + "_screen.getRealMetrics.stack", ".getDeviceInfo", context);

      tmp_json = new JSONObject();
      tmp_json.put("width", 1080);
      tmp_json.put("height", 1820);
      callVCloudSettings_put("screen.getRealSize", tmp_json.toString(), context);
      callVCloudSettings_put(current_pkg_name + "_screen.getRealSize.stack", ".getDeviceInfo", context);

      tmp_json = new JSONObject();
      tmp_json.put("left", 0);
      tmp_json.put("top", 0);
      tmp_json.put("right", 1080);
      tmp_json.put("bottom", 1920);
      callVCloudSettings_put("screen.getCurrentBounds", tmp_json.toString(), context);
      callVCloudSettings_put("screen.getMaximumBounds", tmp_json.toString(), context);
      callVCloudSettings_put(current_pkg_name + "_screen.getCurrentBounds.stack", ".getDeviceInfo", context);
      callVCloudSettings_put(current_pkg_name + "_screen.getMaximumBounds.stack", ".getDeviceInfo", context);

      // **User-Agent**
      callVCloudSettings_put(current_pkg_name + "_user_agent", userAgent, context);

      // **os_ver**
      callVCloudSettings_put(current_pkg_name + "_os_ver", osVer, context);

      // **os_lang**系统语言
      callVCloudSettings_put(current_pkg_name + "_os_lang", osLang, context);

      // **dpi**
      JSONObject densityJson = new JSONObject();
      densityJson.put("density", context.getResources().getDisplayMetrics().density);
      callVCloudSettings_put(current_pkg_name + "_dpi", densityJson.toString(), context);

      // **dpi_f**
      JSONObject realResolutionJson = new JSONObject();
      realResolutionJson.put("width", 411);
      realResolutionJson.put("height", 731);
      callVCloudSettings_put(current_pkg_name + "_dpi_f", realResolutionJson.toString(), context);

      // **tz** (时区)
      callVCloudSettings_put(current_pkg_name + "_tz", tz, context);

      // **isp** (网络运营商)
      android.telephony.TelephonyManager telephonyManager = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      String isp = telephonyManager != null ? telephonyManager.getNetworkOperatorName() : "unknown";
      callVCloudSettings_put(current_pkg_name + "_isp", isp, context);

      // **net** (网络类型：WiFi/流量)
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      if (connectivityManager != null) {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);

        String netType = "Unknown";
        if (capabilities != null) {
          if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            netType = "WiFi";
          } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            netType = "Cellular";
          }
        }

        callVCloudSettings_put(current_pkg_name + "_net", netType, context);
      }

      // **广告标识符 (advertiserId)** 及 **启用状态**
      boolean isAdIdEnabled = true; // 默认启用广告 ID
      String advertiserId = "test-advertiser-id"; // 模拟广告 ID
      callVCloudSettings_put(current_pkg_name + ".advertiserId", advertiserId, context);
      callVCloudSettings_put(current_pkg_name + ".advertiserIdEnabled", String.valueOf(isAdIdEnabled), context);

      // **AppsFlyer 参数**
      callVCloudSettings_put(current_pkg_name + ".af_currentstore", "Google Play", context);
      callVCloudSettings_put(current_pkg_name + ".af_events_api", "2.0", context);
      callVCloudSettings_put(current_pkg_name + ".af_installstore", "official", context);
      callVCloudSettings_put(current_pkg_name + ".af_timestamp", String.valueOf(System.currentTimeMillis()), context);
      callVCloudSettings_put(current_pkg_name + ".af_v", "v1.0", context);
      callVCloudSettings_put(current_pkg_name + ".af_v2", "v2.0", context);

      // **设备信息参数**
      callVCloudSettings_put(current_pkg_name + ".android_id", "android-test-id", context);
      callVCloudSettings_put(current_pkg_name + ".app_version_code", "100", context);
      callVCloudSettings_put(current_pkg_name + ".app_version_name", "1.0.0", context);
      callVCloudSettings_put(current_pkg_name + ".brand", android.os.Build.BRAND, context);
      callVCloudSettings_put(current_pkg_name + ".device", android.os.Build.DEVICE, context);
      callVCloudSettings_put(current_pkg_name + ".model", model, context);
      callVCloudSettings_put(current_pkg_name + ".cpu_clock_speed", cpuClockSpeed, context);

      // **语言及区域**
      String lang = context.getResources().getConfiguration().locale.getLanguage();
      String langCode = context.getResources().getConfiguration().locale.toString();
      callVCloudSettings_put(current_pkg_name + ".lang", lang, context);
      callVCloudSettings_put(current_pkg_name + ".lang_code", langCode, context);
      callVCloudSettings_put(current_pkg_name + ".country", country, context);

      // **传感器模拟数据**
      JSONObject sensorsJson = new JSONObject();
      sensorsJson.put("sN", "TestSensor"); // 传感器名称
      sensorsJson.put("sT", "type_sample"); // 传感器类型
      sensorsJson.put("sV", "1.2"); // 传感器版本
      JSONArray sensorValues = new JSONArray();
      sensorValues.put("v0");
      sensorValues.put("v1");
      sensorValues.put("v2");
      sensorsJson.put("sVE", sensorValues);
      callVCloudSettings_put(current_pkg_name + ".deviceData.sensors.[0]", sensorsJson.toString(), context);

      // **电量、电磁 MCC/MNC**
      callVCloudSettings_put(current_pkg_name + ".batteryLevel", "85", context);
      callVCloudSettings_put(current_pkg_name + ".cell.mcc", "310", context); // MCC: 示例
      callVCloudSettings_put(current_pkg_name + ".cell.mnc", "260", context); // MNC: 示例

      // **日期与时间戳**
      callVCloudSettings_put(current_pkg_name + ".date1", String.valueOf(System.currentTimeMillis()), context);
      callVCloudSettings_put(current_pkg_name + ".date2", String.valueOf(System.nanoTime()), context);

      // **其他示例条目**
      callVCloudSettings_put(current_pkg_name + ".appsflyerKey", "example-key", context);
      callVCloudSettings_put(current_pkg_name + ".appUserId", "test-user-id", context);
      callVCloudSettings_put(current_pkg_name + ".disk", "128GB", context);
      callVCloudSettings_put(current_pkg_name + ".operator", "Fake Operator", context);

      // **gaid** (Google 广告 ID)
      try {
        callVCloudSettings_put(current_pkg_name + "_gaid", gaid, context);
      } catch (Throwable e) {
        Log.e("ChangeDeviceInfoUtil", "Failed to get GAID", e);
      }

    } catch (Throwable e) {
      Log.e("ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
      throw new RuntimeException("Error occurred in changeDeviceInfo", e);
    }

    if (!ShellUtils.hasRootAccess()) {
      Log.e("ChangeDeviceInfoUtil", "Root access is required to execute system property changes");
      return;
    }

    // 设置机型, 直接设置属性
    ShellUtils.execRootCmd("setprop ro.product.brand google");
    ShellUtils.execRootCmd("setprop ro.product.model raven");
    ShellUtils.execRootCmd("setprop ro.product.manufacturer google");
    ShellUtils.execRootCmd("setprop ro.product.device raven");
    ShellUtils.execRootCmd("setprop ro.product.name raven");
    ShellUtils.execRootCmd("setprop ro.build.version.incremental 9325679");
    ShellUtils.execRootCmd("setprop ro.build.fingerprint \"google/raven/raven:13/TQ1A.230105.002/9325679:user/release-keys\"");
    ShellUtils.execRootCmd("setprop ro.board.platform acr980m");

    Native.setBootId("400079ef55a4475558eb60a0544a43d5");

    // 修改drm id
    ShellUtils.execRootCmd("setprop persist.sys.cloud.drm.id 400079ef55a4475558eb60a0544a43d5171258f13fdd48c10026e2847a6fc7a5");

    // 电量模拟需要大于1000
    ShellUtils.execRootCmd("setprop persist.sys.cloud.battery.capacity 5000");

    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_vendor my_gl_vendor");
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_renderer my_gl_renderer");
    // 这个值不能随便改  必须是 OpenGL ES %d.%d 这个格式
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_version \"OpenGL ES 3.2\"");

    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_vendor my_egl_vendor");
    ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_version my_egl_version");

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
      Log.w("Reflection Error", "Method putString not available. Ensure your target Android version supports it.");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getTargetException();
      if (cause instanceof SecurityException) {
        Log.e("Reflection Error", "Permission denied. Ensure WRITE_SECURE_SETTINGS permission is granted.", cause);
      } else {
        Log.e("Reflection Error", "InvocationTargetException during putString invocation", e);
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
