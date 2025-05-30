package com.example.studyapp;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.VpnService;
import android.content.Context;
import android.content.Intent;
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

import com.example.studyapp.autoJS.AutoJsUtil;
import com.example.studyapp.device.ChangeDeviceInfo;
import com.example.studyapp.proxy.CustomVpnService;
import com.example.studyapp.utils.ReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;
    private static final int VPN_REQUEST_CODE = 100; // Adding the missing constant

    private static final int ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // 针对 Android 10 或更低版本检查普通存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE_PERMISSION
                );
            }
        } else {
            // 针对 Android 11 及更高版本检查全文件管理权限
            if (!Environment.isExternalStorageManager()) {
                // 请求权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE);
            }
        }

        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "Network is not available", Toast.LENGTH_SHORT).show();
            finish();
        }
        // 初始化按钮
        Button runScriptButton = findViewById(R.id.run_script_button);
        if (runScriptButton != null) {
            runScriptButton.setOnClickListener(v -> AutoJsUtil.runAutojsScript(this));
        } else {
            Toast.makeText(this, "Button not found", Toast.LENGTH_SHORT).show();
        }

        Button connectButton = findViewById(R.id.connectVpnButton);
        if (connectButton != null) {
            connectButton.setOnClickListener(v -> startProxyVpn(this));
        } else {
            Toast.makeText(this, "Connect button not found", Toast.LENGTH_SHORT).show();
        }

        Button disconnectButton = findViewById(R.id.disconnectVpnButton);
        if (disconnectButton != null) {
            disconnectButton.setOnClickListener(v -> stopProxy(this));
        } else {
            Toast.makeText(this, "Disconnect button not found", Toast.LENGTH_SHORT).show();
        }

        Button modifyDeviceInfoButton = findViewById(R.id.modifyDeviceInfoButton);
        if (modifyDeviceInfoButton != null) {
            modifyDeviceInfoButton.setOnClickListener(v -> ChangeDeviceInfo.changeDeviceInfo(getPackageName(),this));
        } else {
            Toast.makeText(this, "modifyDeviceInfo button not found", Toast.LENGTH_SHORT).show();
        }

        Button resetDeviceInfoButton = findViewById(R.id.resetDeviceInfoButton);
        if (resetDeviceInfoButton != null) {
            resetDeviceInfoButton.setOnClickListener(v -> ChangeDeviceInfo.resetChangedDeviceInfo(getPackageName(),this));
        } else {
            Toast.makeText(this, "resetDeviceInfo button not found", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Storage Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                // 提示权限被拒绝，同时允许用户重新授予权限
                showPermissionExplanationDialog();
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE:
                handleStoragePermissionResult(resultCode);
                break;
            case VPN_REQUEST_CODE:
                handleVpnPermissionResult(resultCode);
                break;
            default:
                break;
        }
    }

    private void handleStoragePermissionResult(int resultCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Storage Permissions granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "请授予所有文件管理权限", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stopProxy(Context context) {
        if (context == null) {
            Log.e("stopProxy", "上下文为空，无法停止服务");
            return;
        }

        if (!isServiceRunning(context, CustomVpnService.class)) {
            Log.w("stopProxy", "服务未运行，无法停止");
            return;
        }

        new Thread(() -> {
            boolean isServiceStopped = true;
            try {
                // 通过反射获取服务实例
                Object instance = ReflectionHelper.getInstance("com.example.studyapp.proxy.CustomVpnService", "instance");
                if (instance != null) {
                    // 获取并调用 stopService 方法
                    Method stopServiceMethod = instance.getClass().getDeclaredMethod("stopService", Intent.class);
                    stopServiceMethod.invoke(instance, intent);
                    Log.d("stopProxy", "服务已成功停止");
                } else {
                    isServiceStopped = false;
                    Log.w("stopProxy", "实例为空，服务可能未启动");
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                isServiceStopped = false;
                Log.e("stopProxy", "无法停止服务: " + e.getMessage(), e);
            } catch (Exception e) {
                isServiceStopped = false;
                Log.e("stopProxy", "停止服务时发生未知错误: " + e.getMessage(), e);
            }

            // 在主线程中更新用户提示
            String message = isServiceStopped ? "VPN 服务已停止" : "停止 VPN 服务失败";
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private Intent intent;
    private void handleVpnPermissionResult(int resultCode) {
        if (resultCode == RESULT_OK) {

            intent = new Intent(this, CustomVpnService.class);

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            } catch (IllegalStateException e) {
                Log.e("handleVpnPermissionResult", "Failed to start VPN service", e);
                showToastOnUiThread(this, "Failed to start VPN service");
            }

        } else {
            // 其他结果代码处理逻辑
            showToastOnUiThread(this, "VPN permission denied or failed.");
            Log.e("handleVpnPermissionResult", "VPN permission denied or failed with resultCode: " + resultCode);
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
        if (AutoJsUtil.scriptResultReceiver != null) {
            unregisterReceiver(AutoJsUtil.scriptResultReceiver);
        }
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        return false;
    }
}