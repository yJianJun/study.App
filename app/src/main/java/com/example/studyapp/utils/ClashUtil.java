package com.example.studyapp.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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

  public static boolean checkProxy(Context context) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    checkClashStatus(context, latch);
    latch.await(); // 等待广播接收器更新状态
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
      throw new IllegalArgumentException("Group name and proxy name must not be empty");
    }
    if (controllerUrl == null || !controllerUrl.matches("^https?://.*")) {
      throw new IllegalArgumentException("Invalid controller URL");
    }

    OkHttpClient client = new OkHttpClient();
    JSONObject json = new JSONObject();
    try {
      json.put("name", proxyName);
    } catch (JSONException e) {
      e.printStackTrace();
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

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        e.printStackTrace();
        System.out.println("Failed to switch proxy: " + e.getMessage());
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        try {
          if (response.body() != null) {
            System.out.println("Switch proxy response: " + response.body().string());
          } else {
            System.out.println("Response body is null");
          }
        } finally {
          response.close();
        }
      }
    });
  }

}
