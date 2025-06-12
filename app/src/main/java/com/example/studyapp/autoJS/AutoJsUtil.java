package com.example.studyapp.autoJS;

import static androidx.core.content.ContextCompat.startActivity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
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
    public static volatile boolean flag;

    public static void runAutojsScript(Context context,String url) {
        // 检查脚本文件
        File scriptFile = new File(Environment.getExternalStorageDirectory(), "script/main.js");
        if (!scriptFile.exists()) {
            runOnUiThread(() -> Toast.makeText(context, "脚本文件未找到: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
            Log.e("AutoJsUtil", "脚本文件未找到, 路径: " + scriptFile.getAbsolutePath());
            return;
        }

        // 检查是否安装 Auto.js
        if (!isAppInstalled("org.autojs.autojs6", context.getPackageManager())) {
            runOnUiThread(() -> Toast.makeText(context, "Auto.js app not installed", Toast.LENGTH_SHORT).show());
            return;
        }

        // 启动 AutoJs
        Intent intent = new Intent();
        intent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.RunIntentActivity");
        intent.putExtra("path", scriptFile.getAbsolutePath());
        intent.putExtra("url", url);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            registerScriptResultReceiver(context);
            context.startActivity(intent);
            Log.i("AutoJsUtil", "脚本运行中：" + scriptFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("AutoJsUtil", "运行脚本失败", e);
            runOnUiThread(() -> Toast.makeText(context, "运行脚本失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
        // 注意：unregisterScriptResultReceiver 不应到此时立即调用
    }

    public static void registerScriptResultReceiver(Context context) {
        // 使用锁进行控制
        synchronized (MainActivity.lock) {
            if (scriptResultReceiver == null) {
                // 创建广播接收器
                scriptResultReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String scriptResult = intent.getStringExtra("result");
                        if (scriptResult != null && !scriptResult.isEmpty()) {
                            Log.d("MainActivity", "Script result received");
                            synchronized (MainActivity.lock){
                                flag = true;
                                MainActivity.lock.notifyAll();
                            }
                        }
                    }
                };
                // 注册广播接收器
                try {
                    IntentFilter filter = new IntentFilter(AUTOJS_SCRIPT_FINISHED_ACTION);
                    Context appContext = context.getApplicationContext();
                    ContextCompat.registerReceiver(appContext, scriptResultReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
                    flag = false;
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to register receiver", e);
                }
            }
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

    private static final String AUTOJS_SCRIPT_FINISHED_ACTION = "org.autojs.SCRIPT_FINISHED";
    private static final String SCRIPT_RESULT_KEY = "result";
    private static final Object lock = new Object();


    public static void unregisterScriptResultReceiver(Context context) {
        synchronized (lock) {
            if (scriptResultReceiver != null) {
                context.getApplicationContext().unregisterReceiver(scriptResultReceiver);
                scriptResultReceiver = null;
            }
        }
    }

    public static void stopAutojsScript(Context context) {
        // 停止运行脚本的 Intent
        Intent stopIntent = new Intent();
        stopIntent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity");
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 检查目标活动是否存在
        boolean activityAvailable = isActivityAvailable(context, "org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity");
        Log.d("AutoJsUtil", "是否找到目标活动: " + activityAvailable);

        if (activityAvailable) {
            try {
                context.startActivity(stopIntent); // 尝试发送停止脚本的 Intent
                Toast.makeText(context, "脚本停止命令已发送", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, "无法发送停止命令，请检查 AutoJs 配置", Toast.LENGTH_SHORT).show();
                Log.e("AutoJsUtil", "发送停止命令时发生错误", e);
            }
        } else {
            Toast.makeText(context, "目标活动未找到或已更改，请检查 AutoJs 配置", Toast.LENGTH_LONG).show();
            Log.e("AutoJsUtil", "目标活动未找到: org.autojs.autojs.external.open.StopServiceActivity");
        }
    }

}
