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
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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



  public static String changeDeviceInfo(String current_pkg_name, Context context) {

    if (bigoDeviceObject == null || afDeviceObject == null) {
      Log.e("ChangeDeviceInfoUtil", "Required device JSON objects are not initialized");
      throw new IllegalStateException("Device initialization failed");
    }

    try {
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
      //String model = deviceObject.optString("model");
      String net = bigoDeviceObject.optString("net");
      int dpi = bigoDeviceObject.optInt("dpi");
      long romFreeExt = bigoDeviceObject.optLong("rom_free_ext");
      String dpiF = bigoDeviceObject.optString("dpi_f");
      int cpuCoreNum = bigoDeviceObject.optInt("cpu_core_num");

      //AF
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
      String lang = afDeviceObject.optString(".lang");



      String url = "https://app.appsflyer.com/com.gateio.gateio?pid=seikoads_int&af_siteid={aff}&c=Cj4jwOBf&af_sub_siteid={aff_sub6}&af_c_id={offer_id}&af_ad={adx_bundle_id}&af_ad_id={affiliate_id}&af_adset_id={offer_id}&af_channel={adx_id}&af_cost_currency={currency}&af_cost_value={payout}&af_adset={transaction_id}&af_click_lookback=7d&af_ip={ip}&af_lang={aff_sub4}&af_ua={ua}&clickid={transaction_id}&advertising_id={aff_sub3}&idfa={aff_sub3}&af_model={model}&af_os_version={os_version}&is_incentivized=false&af_prt=huiimedia";
      Map<String, String> params = new HashMap<>();
      params.put("aff", "12345");
      params.put("aff_sub6", "sub6Value");
      params.put("offer_id", "offer123");
      params.put("adx_bundle_id", "adxBundle123");
      params.put("affiliate_id", "affiliateID123");
      params.put("currency", "USD");
      params.put("payout", "5.0");
      params.put("transaction_id","");
      params.put("ip",HttpUtil.getLocalIpAddress());
      params.put("aff_sub4", "English");
      params.put("aff_sub3", "AdvertisingID123");

      params.put("adx_id", advertiserId);
      params.put("ua",userAgent);
      params.put("model", model);
      params.put("os_version", osVer);

      // 自动处理分辨率信息
      // int widthPixels = Integer.parseInt(resolution.split("x")[0]);
      // int heightPixels = Integer.parseInt(resolution.split("x")[1]);

      // 更新屏幕显示相关参数
      // JSONObject displayMetrics = new JSONObject();
      // displayMetrics.put("widthPixels", widthPixels);
      // displayMetrics.put("heightPixels", heightPixels);
      // displayMetrics.put("densityDpi", dpi);
      // callVCloudSettings_put("screen.device.displayMetrics", displayMetrics.toString(), context);

      //BIGO 替换写死的值为 JSON 动态值
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

      //AF 替换写死的值为 JSON 动态值
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
        return null;
      }

      String ro_product_brand = afDeviceObject.optString("ro.product.brand", "");
      String ro_product_model  = afDeviceObject.optString("ro.product.model", "");
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

      // 设置机型, 直接设置属性
      ShellUtils.execRootCmd("setprop ro.product.brand " + ro_product_brand);
      ShellUtils.execRootCmd("setprop ro.product.model "+ ro_product_model );
      ShellUtils.execRootCmd("setprop ro.product.manufacturer "+ro_product_manufacturer );
      ShellUtils.execRootCmd("setprop ro.product.device "+ ro_product_device);
      ShellUtils.execRootCmd("setprop ro.product.name "+ro_product_name );
      ShellUtils.execRootCmd("setprop ro.build.version.incremental "+ro_build_version_incremental);
      ShellUtils.execRootCmd("setprop ro.build.fingerprint "+ro_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.odm.build.fingerprint "+ro_odm_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.product.build.fingerprint "+ro_product_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.system.build.fingerprint "+ro_system_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.system_ext.build.fingerprint "+ro_system_ext_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.vendor.build.fingerprint "+ro_vendor_build_fingerprint );
      ShellUtils.execRootCmd("setprop ro.board.platform "+ro_build_platform );

     // Native.setBootId(bootId);
      // 修改drm id
      ShellUtils.execRootCmd("setprop persist.sys.cloud.drm.id "+persist_sys_cloud_drm_id);
      // 电量模拟需要大于1000
      ShellUtils.execRootCmd("setprop persist.sys.cloud.battery.capacity "+persist_sys_cloud_battery_capacity);
      ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_vendor "+persist_sys_cloud_gpu_gl_vendor);
      ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_renderer "+persist_sys_cloud_gpu_gl_renderer);
      // 这个值不能随便改  必须是 OpenGL ES %d.%d 这个格式
      ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.gl_version "+persist_sys_cloud_gpu_gl_version);
      ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_vendor "+persist_sys_cloud_gpu_egl_vendor);
      ShellUtils.execRootCmd("setprop persist.sys.cloud.gpu.egl_version "+persist_sys_cloud_gpu_egl_version);

      // 填充占位符
      return HttpUtil.fillUrlPlaceholders(url, params);
    } catch (Throwable e) {
      Log.e("ChangeDeviceInfoUtil", "Error occurred while changing device info", e);
      throw new RuntimeException("Error occurred in changeDeviceInfo", e);
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
