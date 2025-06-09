package com.example.studyapp.device;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import com.example.studyapp.utils.ShellUtils;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ChangeDeviceInfoUtil {

    public static void changeDeviceInfo(String current_pkg_name,Context context) {
        // 指定包名优先级高于全局
        callVCloudSettings_put(current_pkg_name + "_android_id", "my123456",context);
        callVCloudSettings_put(current_pkg_name + "_screen_brightness", "100",context);
        callVCloudSettings_put(current_pkg_name + "_adb_enabled", "1",context);
        callVCloudSettings_put(current_pkg_name + "_development_settings_enabled", "1",context);

        callVCloudSettings_put("pm_list_features", "my_pm_list_features",context);
        callVCloudSettings_put("pm_list_libraries", "my_pm_list_libraries",context);
        callVCloudSettings_put("system.http.agent", "my_system.http.agent",context);
        callVCloudSettings_put("webkit.http.agent", "my_webkit.http.agent",context);

        callVCloudSettings_put("global_android_id", "123456",context);

        callVCloudSettings_put("anticheck_pkgs", current_pkg_name,context);

        try {

            JSONObject pkg_info_json = new JSONObject();
            pkg_info_json.put("versionName", "1.0.0");
            pkg_info_json.put("versionCode", 100);
            pkg_info_json.put("firstInstallTime", 1);
            pkg_info_json.put("lastUpdateTime", 1);
            callVCloudSettings_put("com.fk.tools_pkgInfo", pkg_info_json.toString(),context);

            JSONObject tmp_json = new JSONObject();
            tmp_json.put("widthPixels", 1080);
            tmp_json.put("heightPixels", 1920);
            tmp_json.put("densityDpi", 440);
            tmp_json.put("xdpi", 160);
            tmp_json.put("ydpi", 160);
            tmp_json.put("density", 3.0);
            tmp_json.put("scaledDensity", 3.0);
            callVCloudSettings_put("screen.getDisplayMetrics", tmp_json.toString(),context);
            callVCloudSettings_put("screen.getMetrics", tmp_json.toString(),context);
            callVCloudSettings_put("screen.getRealMetrics", tmp_json.toString(),context);
            callVCloudSettings_put(current_pkg_name + "_screen.getDisplayMetrics.stack", ".getDeviceInfo",context);
            String stackInfo = Thread.currentThread().getStackTrace()[2].toString();
            callVCloudSettings_put(current_pkg_name + "_screen.getMetrics.stack", stackInfo, context);
            callVCloudSettings_put(current_pkg_name + "_screen.getRealMetrics.stack", ".getDeviceInfo",context);


            tmp_json = new JSONObject();
            tmp_json.put("width", 1080);
            tmp_json.put("height", 1820);
            callVCloudSettings_put("screen.getRealSize", tmp_json.toString(),context);
            callVCloudSettings_put(current_pkg_name + "_screen.getRealSize.stack", ".getDeviceInfo",context);


            tmp_json = new JSONObject();
            tmp_json.put("left", 0);
            tmp_json.put("top", 0);
            tmp_json.put("right", 1080);
            tmp_json.put("bottom", 1920);
            callVCloudSettings_put("screen.getCurrentBounds", tmp_json.toString(),context);
            callVCloudSettings_put("screen.getMaximumBounds", tmp_json.toString(),context);
            callVCloudSettings_put(current_pkg_name + "_screen.getCurrentBounds.stack", ".getDeviceInfo",context);
            callVCloudSettings_put(current_pkg_name + "_screen.getMaximumBounds.stack", ".getDeviceInfo",context);

            // **User-Agent**
            String defaultUserAgent = System.getProperty("http.agent");  // 默认 User-Agent
            callVCloudSettings_put(current_pkg_name + "_user_agent", defaultUserAgent, context);

            // **os_ver**
            String osVer = System.getProperty("os.version");  // 系统版本
            callVCloudSettings_put(current_pkg_name + "_os_ver", osVer, context);

            // **os_lang**
            String osLang = context.getResources().getConfiguration().locale.getLanguage();  // 系统语言
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
            String tz = java.util.TimeZone.getDefault().getID();  // 系统时区
            callVCloudSettings_put(current_pkg_name + "_tz", tz, context);

            // **isp** (网络运营商)
            android.telephony.TelephonyManager telephonyManager = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String isp = telephonyManager != null ? telephonyManager.getNetworkOperatorName() : "unknown";
            callVCloudSettings_put(current_pkg_name + "_isp", isp, context);

            // **country**
            String country = context.getResources().getConfiguration().locale.getCountry();
            callVCloudSettings_put(current_pkg_name + "_country", country, context);

            // **net** (网络类型：WiFi/流量)
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            String netType = (connectivityManager != null &&
                connectivityManager.getActiveNetworkInfo() != null &&
                connectivityManager.getActiveNetworkInfo().isConnected()) ?
                connectivityManager.getActiveNetworkInfo().getTypeName() : "unknown";
            callVCloudSettings_put(current_pkg_name + "_net", netType, context);

            // **gaid** (Google 广告 ID)
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                String gaid = adInfo != null ? adInfo.getId() : "unknown";
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

    public static void resetChangedDeviceInfo(String current_pkg_name,Context context) {
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
