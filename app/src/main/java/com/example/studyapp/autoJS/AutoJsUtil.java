package com.example.studyapp.autoJS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Environment;
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

    public static void runAutojsScript(Context context) {
        // 定义脚本文件路径
        File scriptFile = new File(Environment.getExternalStorageDirectory(), "脚本/chromium.js");

        // 检查文件是否存在
        if (!scriptFile.exists()) {
            Toast.makeText(context, "Script file not found: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 Auto.js 应用是否安装
        if (!isAppInstalled("org.autojs.autojs6",context.getPackageManager())) {
            Toast.makeText(context, "Auto.js app not installed", Toast.LENGTH_SHORT).show();
            return;
        }

        // 准备启动 Auto.js 的 Intent
        Intent intent = new Intent();
        intent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.RunIntentActivity");
        intent.putExtra("path", scriptFile.getAbsolutePath()); // 传递脚本路径
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动 Auto.js
        try {
            // 模拟：通过广播监听脚本运行结果
            registerScriptResultReceiver(context); // 注册结果回调监听（假设脚本通过广播返回结果）
            context.startActivity(intent);
            Toast.makeText(context, "Running script: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to run script", Toast.LENGTH_SHORT).show();
        }
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
