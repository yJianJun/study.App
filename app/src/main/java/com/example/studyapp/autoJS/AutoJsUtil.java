package com.example.studyapp.autoJS;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.studyapp.MainActivity;
import com.example.studyapp.request.ScriptResultRequest;
import com.example.studyapp.service.CloudPhoneManageService;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AutoJsUtil {

    public static BroadcastReceiver scriptResultReceiver;

    public static void runAutojsScript(Context context,String url) {
        // 检查脚本文件
        File scriptFile = new File(Environment.getExternalStorageDirectory(), "script/main.js");
        if (!scriptFile.exists()) {
            runOnUiThread(() -> Toast.makeText(context, "Script file not found: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
            return;
        }

        // 检查是否安装 Auto.js
        if (!isAppInstalled("org.autojs.autojs6", context.getPackageManager())) {
            runOnUiThread(() -> Toast.makeText(context, "Auto.js app not installed", Toast.LENGTH_SHORT).show());
            return;
        }

        // 开始运行脚本
        Intent intent = new Intent();
        intent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.RunIntentActivity");
        intent.putExtra("path", scriptFile.getAbsolutePath());
        intent.putExtra("url",url);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            runOnUiThread(() -> Toast.makeText(context, "Running script: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(context, "Failed to run script", Toast.LENGTH_SHORT).show());
        }
    }

    public static void stopAutojsScript(Context context) {
        // 停止运行脚本
        Intent stopIntent = new Intent();
        stopIntent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity");
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 检查目标活动是否存在
        if (isActivityAvailable(context, "org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity")) {
            try {
                context.startActivity(stopIntent); // 发送停止脚本的 Intent
                Toast.makeText(context, "脚本停止管理已发送", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, "无法发送停止命令，请检查 AutoJs 配置", Toast.LENGTH_SHORT).show();
                Log.e("AutoJsUtil", "Error occurred when trying to stop AutoJs script", e);
            }
        } else {
            Toast.makeText(context, "目标活动未找到：请确认 AutoJs 配置", Toast.LENGTH_LONG).show();
            Log.e("AutoJsUtil", "Activity not found: org.autojs.autojs.external.open.StopServiceActivity");
        }
    }

    private static boolean isActivityAvailable(Context context, String packageName, String className) {
        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        PackageManager pm = context.getPackageManager();
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    // 在主线程运行
    private static void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    // 检查目标应用是否安装
    public static boolean isAppInstalled(String packageName,PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void registerScriptResultReceiver(Context context) {
        // 创建广播接收器
        scriptResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // 获取脚本运行结果（假设结果通过 "result" 键返回）
                String scriptResult = intent.getStringExtra("result");
                if (scriptResult != null && !scriptResult.isEmpty()) {
                    // 处理结果并发送给服务端
                    sendResultToServer(scriptResult,context);
                }
            }
        };

        // 注册接收器（假设 Auto.js 广播动作为 "org.autojs.SCRIPT_FINISHED"）
        IntentFilter filter = new IntentFilter("org.autojs.SCRIPT_FINISHED");

        // 使用 ContextCompat.registerReceiver 注册，并设置为 RECEIVER_EXPORTED
        ContextCompat.registerReceiver(
                context, // 当前上下文
                scriptResultReceiver, // 自定义的 BroadcastReceiver
                filter, // IntentFilter
                ContextCompat.RECEIVER_EXPORTED // 设置为非导出广播接收器
        );
    }

    public static void sendResultToServer(String scriptResult,Context context) {
        // 使用 Retrofit 或 HttpURLConnection 实现服务端 API 调用
        Toast.makeText(context, "Sending result to server: " + scriptResult, Toast.LENGTH_SHORT).show();

        // 示例：用 Retrofit 设置服务端请求
        // 创建 Retrofit 的实例
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://your-server-url.com/") // 替换为服务端 API 地址
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // 定义 API 接口
        CloudPhoneManageService api = retrofit.create(CloudPhoneManageService.class);

        // 构建请求体并发送请求
        Call<Void> call = api.sendScriptResult(new ScriptResultRequest(scriptResult)); // 假设参数是 com.example.studyapp.request.ScriptResultRequest 对象
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(context, "Result sent successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to send result: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(context, "Error sending result: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
