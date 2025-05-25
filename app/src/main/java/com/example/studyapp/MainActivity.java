package com.example.studyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.net.VpnService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.example.studyapp.proxy.CustomVpnService;
import com.example.studyapp.request.ScriptResultRequest;
import com.example.studyapp.service.CloudPhoneManageService;

import java.io.File;
import java.lang.ref.WeakReference;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;
    private static final int VPN_REQUEST_CODE = 100; // Adding the missing constant
    private static final int REQUEST_CODE_VPN = 2;

    private BroadcastReceiver scriptResultReceiver;

    private ActivityResultLauncher<Intent> vpnRequestLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
        }


        // 查找按钮对象
        Button runScriptButton = findViewById(R.id.run_script_button);
        if (runScriptButton != null) {
            runScriptButton.setOnClickListener(view -> runAutojsScript()); // 设置点击事件
        } else {
            Toast.makeText(this, "Button not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void startProxyVpn(Context context) {
        if (!isNetworkAvailable(context)) {
            Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!(context instanceof Activity)) {
            Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
            return;
        }
        Activity activity = (Activity) context;

        try {
            startProxyServer(activity); // 在主线程中调用
        } catch (IllegalStateException e) {
            Toast.makeText(context, "Failed to start VPN: VPN Service illegal state", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Failed to start VPN: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
        }
    }

    private void startProxyServer(Activity activity) {
        // 请求 VPN 权限
        Intent vpnPrepareIntent = VpnService.prepare(activity);
        if (vpnPrepareIntent != null) {
            // 如果尚未授予权限，请求权限，等待结果回调
            startActivityForResult(vpnPrepareIntent, VPN_REQUEST_CODE);
        } else {
            // 如果已经授予权限，直接调用 onActivityResult 模拟结果处理
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    private void showToastOnUiThread(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();

                if (!isNetworkAvailable(this)) {
                    Toast.makeText(this, "Network is not available", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 启动 VPN 服务
                startProxyVpn(this);
            } else {
                showPermissionExplanationDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            // Permission granted, now you can start your VpnService
            Intent intent = new Intent(this, CustomVpnService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.startForegroundService(intent);
                } else {
                    this.startService(intent);
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
                showToastOnUiThread(this, "Failed to start VPN service");
            }
        } else {
            // Permission denied or an error occurred
            Log.e("VPNSetup", "VPN permission denied or cancelled by user.");
            // Handle denial: show a message to the user, disable VPN functionality, etc.
        }
    }

    private void showPermissionExplanationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required")
                .setMessage("Storage Permission is required for the app to function. Please enable it in Settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scriptResultReceiver != null) {
            unregisterReceiver(scriptResultReceiver);
        }
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return false;
    }


    private void runAutojsScript() {
        // 定义脚本文件路径
        File scriptFile = new File(Environment.getExternalStorageDirectory(), "脚本/chromium.js");

        // 检查文件是否存在
        if (!scriptFile.exists()) {
            Toast.makeText(this, "Script file not found: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 Auto.js 应用是否安装
        if (!isAppInstalled("org.autojs.autojs6")) {
            Toast.makeText(this, "Auto.js app not installed", Toast.LENGTH_SHORT).show();
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
            registerScriptResultReceiver(); // 注册结果回调监听（假设脚本通过广播返回结果）
            startActivity(intent);
            Toast.makeText(this, "Running script: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to run script", Toast.LENGTH_SHORT).show();
        }
    }

    // 检查目标应用是否安装
    private boolean isAppInstalled(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void registerScriptResultReceiver() {
        // 创建广播接收器
        scriptResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // 获取脚本运行结果（假设结果通过 "result" 键返回）
                String scriptResult = intent.getStringExtra("result");
                if (scriptResult != null && !scriptResult.isEmpty()) {
                    // 处理结果并发送给服务端
                    sendResultToServer(scriptResult);
                }
            }
        };

        // 注册接收器（假设 Auto.js 广播动作为 "org.autojs.SCRIPT_FINISHED"）
        IntentFilter filter = new IntentFilter("org.autojs.SCRIPT_FINISHED");

        // 使用 ContextCompat.registerReceiver 注册，并设置为 RECEIVER_EXPORTED
        ContextCompat.registerReceiver(
                this, // 当前上下文
                scriptResultReceiver, // 自定义的 BroadcastReceiver
                filter, // IntentFilter
                ContextCompat.RECEIVER_EXPORTED // 设置为非导出广播接收器
        );
    }

    private void sendResultToServer(String scriptResult) {
        // 使用 Retrofit 或 HttpURLConnection 实现服务端 API 调用
        Toast.makeText(this, "Sending result to server: " + scriptResult, Toast.LENGTH_SHORT).show();

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
                    Toast.makeText(MainActivity.this, "Result sent successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to send result: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error sending result: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}