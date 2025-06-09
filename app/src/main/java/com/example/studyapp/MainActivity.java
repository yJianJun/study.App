package com.example.studyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.studyapp.autoJS.AutoJsUtil;
import com.example.studyapp.device.ChangeDeviceInfoUtil;

import com.example.studyapp.utils.ClashUtil;
import com.example.studyapp.worker.CheckAccessibilityWorker;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

  private static final int ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE = 1001;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    System.setProperty("java.library.path", this.getApplicationInfo().nativeLibraryDir);
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

    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(CheckAccessibilityWorker.class, 15, TimeUnit.MINUTES)
        .build();
    WorkManager.getInstance(this).enqueue(workRequest);

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
      disconnectButton.setOnClickListener(v -> ClashUtil.stopProxy(this));
    } else {
      Toast.makeText(this, "Disconnect button not found", Toast.LENGTH_SHORT).show();
    }

    Button switchVpnButton = findViewById(R.id.switchVpnButton);
    if (switchVpnButton != null) {
      switchVpnButton.setOnClickListener(v -> ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170"));
    } else {
      Toast.makeText(this, "Disconnect button not found", Toast.LENGTH_SHORT).show();
    }

    Button modifyDeviceInfoButton = findViewById(R.id.modifyDeviceInfoButton);
    if (modifyDeviceInfoButton != null) {
      modifyDeviceInfoButton.setOnClickListener(v -> ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170"));
    } else {
      Toast.makeText(this, "modifyDeviceInfo button not found", Toast.LENGTH_SHORT).show();
    }

    Button resetDeviceInfoButton = findViewById(R.id.resetDeviceInfoButton);
    if (resetDeviceInfoButton != null) {
      resetDeviceInfoButton.setOnClickListener(v -> ChangeDeviceInfoUtil.resetChangedDeviceInfo(getPackageName(), this));
    } else {
      Toast.makeText(this, "resetDeviceInfo button not found", Toast.LENGTH_SHORT).show();
    }

    // try {
    //   if (!ClashUtil.checkProxy(this)) {
    //     startProxyVpn(this);
    //   }else {
    //     ClashUtil.switchProxyGroup("GLOBAL","us", "127.0.0.1:6170");
    //   };
    //     ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this);
    //     AutoJsUtil.runAutojsScript(this);
    // } catch (InterruptedException e) {
    //   throw new RuntimeException(e);
    // }
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
      ClashUtil.startProxy(context); // 在主线程中调用
    } catch (IllegalStateException e) {
      Toast.makeText(context, "Failed to start VPN: VPN Service illegal state", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(context, "Failed to start VPN: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
    }
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
