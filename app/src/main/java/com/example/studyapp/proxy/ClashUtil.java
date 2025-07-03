package com.example.studyapp.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.example.studyapp.utils.LogFileUtil;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @Time: 2025/6/9 11:13
 * @Creator: 初屿贤
 * @File: ClashUtil
 * @Project: study.App
 * @Description:
 */
public class ClashUtil {

  public static void startProxy(Context context) {
    Intent intent = new Intent("com.github.kr328.clash.intent.action.SESSION_CREATE");
    intent.putExtra("profile", "default"); // 可选择您在 Clash 中配置的 Profile
    context.sendBroadcast(intent);
  }

  public static void stopProxy(Context context) {
    new Thread(() -> {
      Intent intent = new Intent("com.github.kr328.clash.intent.action.SESSION_DESTROY");
      context.sendBroadcast(intent);
    }).start();
  }

  private static volatile boolean isRunning = false;

  private static final BroadcastReceiver clashStatusReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      isRunning = intent.getBooleanExtra("isRunning", false);
      Log.d("ClashUtil", "Clash Status: " + isRunning);
    }
  };

  public static boolean checkProxy(Context context) {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      checkClashStatus(context, latch);
      latch.await(); // 等待广播接收器更新状态
    } catch (InterruptedException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "checkProxy: Waiting interrupted", e);
      Thread.currentThread().interrupt(); // 重新设置中断状态
      return false; // 返回默认状态或尝试重试
    }
    return isRunning;
  }

  public static void checkClashStatus(Context context, CountDownLatch latch) {
    IntentFilter intentFilter = new IntentFilter("com.github.kr328.clash.intent.action.SESSION_STATE");
    BroadcastReceiver clashStatusReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        isRunning = intent.getBooleanExtra("isRunning", false);
        Log.d("ClashUtil", "Clash Status: " + isRunning);
        latch.countDown(); // 状态更新完成，释放锁
      }
    };
    ContextCompat.registerReceiver(context, clashStatusReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    Intent queryIntent = new Intent("com.github.kr328.clash.intent.action.SESSION_QUERY");
    context.sendBroadcast(queryIntent);
  }

  public static void unregisterReceiver(Context context) {
    context.unregisterReceiver(clashStatusReceiver);
  }

  public static void switchProxyGroup(String groupName, String proxyName, String controllerUrl) {
    if (groupName == null || groupName.trim().isEmpty() || proxyName == null || proxyName.trim().isEmpty()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "switchProxyGroup: Invalid arguments", null);
      throw new IllegalArgumentException("Group name and proxy name must not be empty");
    }
    if (controllerUrl == null || !controllerUrl.matches("^https?://.*")) {
      LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "switchProxyGroup: Invalid controller URL", null);
      throw new IllegalArgumentException("Invalid controller URL");
    }

    OkHttpClient client = new OkHttpClient();
    JSONObject json = new JSONObject();
    try {
      json.put("name", proxyName);
    } catch (JSONException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "switchProxyGroup: JSON error", e);
    }
    String jsonBody = json.toString();

    MediaType JSON = MediaType.get("application/json; charset=utf-8");
    RequestBody requestBody = RequestBody.create(JSON, jsonBody);

    HttpUrl url = HttpUrl.parse(controllerUrl)
        .newBuilder()
        .addPathSegments("proxies/" + groupName)
        .build();

    Request request = new Request.Builder()
        .url(url)
        .put(requestBody)
        .build();

    try {
      Response response = client.newCall(request).execute();

      if (response.isSuccessful() && response.body() != null) {
        LogFileUtil.logAndWrite(Log.INFO, "ClashUtil", "switchProxyGroup: Switch proxy response", null);
      } else {
        LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "switchProxyGroup: Response is not successful or body is null", null);
      }

      response.close();
    } catch (IOException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ClashUtil", "switchProxyGroup: Failed to switch proxy", e);
      System.out.println("Failed to switch proxy: " + e.getMessage());
    }
  }

  public static boolean checkCountryIsUS() {
    Request request = new Request.Builder()
        .url("http://ipinfo.io/json")
        .build();
    OkHttpClient client = new OkHttpClient();
    try (Response response = client.newCall(request).execute()) { // Synchronous call
      if (!response.isSuccessful()) {
        // Server returned an error
        Log.e("ClashUtil", "OkHttp request unsuccessful: " + response.code());
        // Consider how to handle this error synchronously.
        // Maybe throw an exception or return a specific error indicator.
        return false; // Or throw new IOException("Request failed with code " + response.code());
      }

      try (ResponseBody responseBody = response.body()) {
        if (responseBody == null) {
          Log.e("ClashUtil", "Response body is null");
          return false; // Or throw new IOException("Response body is null");
        }

        String jsonData = responseBody.string();
        JSONObject jsonObject = new JSONObject(jsonData);
        String country = jsonObject.optString("country");
        boolean isUS = "US".equalsIgnoreCase(country);

        if (isUS) {
          Log.i("ClashUtil", "Country is US. Full data: " + jsonData);
        } else {
          Log.i("ClashUtil", "Country is NOT US. It is: " + (country.isEmpty() ? "未知" : country) + ". Full data: " + jsonData);
        }
        return isUS;

      } catch (JSONException e) {
        Log.e("ClashUtil", "JSON parsing error: ", e);
        // Consider re-throwing or returning an error indicator
        return false;
      } catch (IOException e) {
        Log.e("ClashUtil", "IOException reading response body: ", e);
        // Consider re-throwing or returning an error indicator
        return false;
      }
    } catch (IOException e) {
      // Network request failed
      Log.e("ClashUtil", "OkHttp request failed: ", e);
      // Consider re-throwing or returning an error indicator
      return false;
    }
  }

}
