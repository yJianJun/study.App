package com.example.studyapp.device;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.util.Log;

import com.example.studyapp.utils.HttpUtil;
import com.example.studyapp.utils.ShellUtils;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ChangeDeviceInfoUtil {


  private static  JSONObject bigoDeviceObject;

  private static  JSONObject afDeviceObject;

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

  public static void initialize(String country, int tag) {
    executorService.submit(() -> {
      try {
        String bigoJson = HttpUtil.requestGet(buildBigoUrl(country, tag));
        String afJson = HttpUtil.requestGet(buildAfUrl(country, tag ));

        bigoDeviceObject = new JSONObject(bigoJson).optJSONObject("device");
        afDeviceObject = new JSONObject(afJson).optJSONObject("device");

        if (bigoDeviceObject == null || afDeviceObject == null) {
          throw new JSONException("Device object is missing in the response JSON");
        }

        Log.d("Debug", "bigoDeviceObject: " + bigoDeviceObject.toString());
        Log.d("Debug", "afDeviceObject: " + afDeviceObject.toString());
      } catch (Exception e) {
        Log.e("Error", "Failed to load or parse the response JSON", e);
      }
    });
  }



  public static void changeDeviceInfo(String current_pkg_name, Context context) {

    try {
      // BIGO
      String cpuClockSpeed = bigoDeviceObject.getString("cpu_clock_speed");
      String gaid = bigoDeviceObject.getString("gaid");
      String userAgent = bigoDeviceObject.getString("User-Agent");
      String osLang = bigoDeviceObject.getString("os_lang");
      String osVer = bigoDeviceObject.getString("os_ver");
      String tz = bigoDeviceObject.getString("tz");
      String systemCountry = bigoDeviceObject.getString("system_country");
      String simCountry = bigoDeviceObject.getString("sim_country");
      long romFreeIn = bigoDeviceObject.getLong("rom_free_in");
      String resolution = bigoDeviceObject.getString("resolution");
      String vendor = bigoDeviceObject.getString("vendor");
      int batteryScale = bigoDeviceObject.getInt("bat_scale");
      //String model = deviceObject.getString("model");
      String net = bigoDeviceObject.getString("net");
      int dpi = bigoDeviceObject.getInt("dpi");
      long romFreeExt = bigoDeviceObject.getLong("rom_free_ext");
      String dpiF = bigoDeviceObject.getString("dpi_f");
      int cpuCoreNum = bigoDeviceObject.getInt("cpu_core_num");
      // 自动处理分辨率信息
      // int widthPixels = Integer.parseInt(resolution.split("x")[0]);
      // int heightPixels = Integer.parseInt(resolution.split("x")[1]);

      // 更新屏幕显示相关参数
      // JSONObject displayMetrics = new JSONObject();
      // displayMetrics.put("widthPixels", widthPixels);
      // displayMetrics.put("heightPixels", heightPixels);
      // displayMetrics.put("densityDpi", dpi);
      // callVCloudSettings_put("screen.device.displayMetrics", displayMetrics.toString(), context);

      callVCloudSettings_put(current_pkg_name + ".system_country", systemCountry, context);
      callVCloudSettings_put(current_pkg_name + ".sim_country", simCountry, context);
      callVCloudSettings_put(current_pkg_name + ".rom_free_in", String.valueOf(romFreeIn), context);
      callVCloudSettings_put(current_pkg_name + ".resolution", resolution, context);
      callVCloudSettings_put(current_pkg_name + ".vendor", vendor, context);
      callVCloudSettings_put(current_pkg_name + ".battery_scale", String.valueOf(batteryScale), context);
      callVCloudSettings_put(current_pkg_name + ".os_lang", osLang, context);
      //callVCloudSettings_put(current_pkg_name + ".model", model, context);
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

      // AF
      String advertiserId = afDeviceObject.getString(".advertiserId");
      String model = afDeviceObject.getString(".model");
      String brand = afDeviceObject.getString(".brand");
      String androidId = afDeviceObject.getString(".android_id");
      int xPixels = afDeviceObject.optInt(".deviceData.dim.x_px");
      int yPixels = afDeviceObject.optInt(".deviceData.dim.y_px");
      int densityDpi = afDeviceObject.optInt(".deviceData.dim.d_dpi");
      String country = afDeviceObject.getString(".country");
      String batteryLevel = afDeviceObject.getString(".batteryLevel");
      String stackInfo = Thread.currentThread().getStackTrace()[2].toString();
      String product = afDeviceObject.getString(".product");
      String network = afDeviceObject.getString(".network");
      String langCode = afDeviceObject.getString(".lang_code");
      String cpuAbi = afDeviceObject.getString(".deviceData.cpu_abi");
      int yDp = afDeviceObject.getInt(".deviceData.dim.ydp");
      String lang = afDeviceObject.getString(".lang");
      // 替换写死的值为 JSON 动态值
      callVCloudSettings_put(current_pkg_name + ".advertiserId", advertiserId, context);
      callVCloudSettings_put(current_pkg_name + ".model", model, context);
      callVCloudSettings_put(current_pkg_name + ".brand", brand, context);
      callVCloudSettings_put(current_pkg_name + ".android_id", androidId, context);
      callVCloudSettings_put(current_pkg_name + ".lang", lang, context);
      callVCloudSettings_put(current_pkg_name + ".country", country, context);
      callVCloudSettings_put(current_pkg_name + ".batteryLevel", batteryLevel, context);
      callVCloudSettings_put(current_pkg_name + "_screen.getMetrics.stack", stackInfo, context);
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
