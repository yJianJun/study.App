package com.example.retention;

import static com.example.retention.task.TaskUtil.infoUpload;
import static com.example.retention.utils.Utils.isNetworkAvailable;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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

import com.example.retention.R;
import com.example.retention.autoJS.AutoJsUtil;
import com.example.retention.config.CountryCode;
import com.example.retention.device.ArmCloudApiClient;
import com.example.retention.device.ChangeDeviceInfoUtil;

import com.example.retention.proxy.ClashUtil;
import com.example.retention.service.MyAccessibilityService;
import com.example.retention.task.TaskUtil;
import com.example.retention.utils.LogFileUtil;
import com.example.retention.worker.CheckAccessibilityWorker;
import com.example.retention.worker.LoadDeviceWorker;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

  private static final int ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE = 1001;

  public static ExecutorService executorService;

  public static ArmCloudApiClient armClient;

  // 假设我们从配置文件中提取出了以下 name 项数据（仅为部分示例数据）
  private final String[] proxyNames = {
      "mr", "sr", "bq", "ml", "ht", "ga", "mk", "by", "pr", "hr", "hu",
      "in", "gt", "at", "kh", "bn", "mg", "kr", "ca", "gh", "ma", "md",
      "je", "pa", "ba", "mm", "ir", "gy", "mt", "ae", "es", "ng", "ls",
      "ag", "pk", "bd", "kn", "mw", "ve", "hk", "cv", "hn", "tm", "us",
      "cz", "ly", "gb", "kz", "it", "bh", "sn", "fi", "co", "sx", "bm",
      "fj", "cw", "st", "bw", "fr", "bb", "tg", "ci", "gd", "ne", "bj",
      "nz", "rs", "do", "cl", "lb", "nl", "re", "aw", "ug", "sv", "ar",
      "jo", "bg", "jp", "rw", "py", "mn", "ec", "uz", "ro", "cu", "gu",
      "xk", "sy", "so", "zm", "tz", "ni", "sc", "my", "gf", "na", "zw",
      "la", "et", "ao", "ua", "om", "np", "mx", "mz", "dm", "ye", "gi",
      "cr", "cm", "ph", "am", "th", "ch", "br", "sd", "ie", "bo", "bs",
      "tc", "vg", "pe", "sa", "dk", "tn", "ee", "jm", "lc", "pt", "qa",
      "ge", "ps"
  };

  // 初始化 ExecutorService
  private void initializeExecutorService() {
    if (executorService == null || executorService.isShutdown()) {
      executorService = new ThreadPoolExecutor(
          1,  // 核心线程数
          1,  // 最大线程数
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<>(50), // 阻塞队列
          new ThreadPoolExecutor.AbortPolicy() // 拒绝策略
      );
    }
  }

  private String getAndroidId() {
    return "FyZqWrStUvOpKlMn";
  }


  private static final int REQUEST_CODE_PERMISSIONS = 100;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogFileUtil.initialize(this);
    setContentView(R.layout.activity_main);

    logInfo("onCreate: Initializing application");

    initializeExecutorService();

    System.setProperty("java.library.path", getApplicationInfo().nativeLibraryDir);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
            REQUEST_CODE_STORAGE_PERMISSION
        );
      }
    } else {
      if (!Environment.isExternalStorageManager()) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE);
      }
    }

    if (!isNetworkAvailable(this)) {
      showToast("网络不可用");
      logError("Network not available, closing app.");
      finish();
      return;
    }

    logInfo("onCreate: Setting up work manager");
    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(CheckAccessibilityWorker.class, 15, TimeUnit.MINUTES)
        .build();
    WorkManager.getInstance(this).enqueue(workRequest);

    logInfo("onCreate: Setting up UI components");

    setupButton(R.id.run_script_button, v -> AutoJsUtil.runAutojsScript(this));
    setupButton(R.id.connectVpnButton, v -> startProxyVpn(this));
    setupButton(R.id.disconnectVpnButton, v -> ClashUtil.stopProxy(this));
    setupButton(R.id.switchVpnButton, v -> ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170"));

    armClient = new ArmCloudApiClient();

    setupButton(R.id.modifyDeviceInfoButton, v -> ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this, armClient));
    setupButton(R.id.resetDeviceInfoButton, v -> ChangeDeviceInfoUtil.resetChangedDeviceInfo(getPackageName(), this));

    Button executeButton = findViewById(R.id.execute_button);
    Button stopExecuteButton = findViewById(R.id.stop_execute_button);

    if (executeButton != null) {
      executeButton.setOnClickListener(v -> {
        executeButton.setEnabled(false);
        startLoadWork();
      });
    }

    if (stopExecuteButton != null) {
      stopExecuteButton.setOnClickListener(v -> {
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG);
        executeButton.setEnabled(true);
      });
    } else {
      showToast("Stop button not found");
    }
  }


  private void setupButton(int resId, View.OnClickListener listener) {
    Button button = findViewById(resId);
    if (button != null) {
      button.setOnClickListener(listener);
    } else {
      String msg = getResources().getResourceEntryName(resId) + " not found";
      logWarn(msg);
      showToast(msg);
    }
  }

  private static String WORK_TAG = "LOAD_WORK";

  private void startLoadWork() {
    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.
        Builder(LoadDeviceWorker.class, 30, TimeUnit.MINUTES)
        .setInitialDelay(0, TimeUnit.SECONDS)
        .addTag(WORK_TAG)
        .build();
    WorkManager.getInstance(this).enqueue(workRequest);
  }

  public static final LinkedBlockingQueue<String> scriptResultQueue = new LinkedBlockingQueue<>();


  private void startProxyVpn(Context context) {
    if (!isNetworkAvailable(context)) {
      Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Network is not available.", null);
      return;
    }

    if (!(context instanceof Activity)) {
      Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Context is not an Activity.", null);
      return;
    }

    try {
      ClashUtil.startProxy(context);
      ClashUtil.switchProxyWithPort(CountryCode.switchCountry());
      // ClashUtil.switchProxyGroup("PROXY", "my-socks5-proxy", "http://127.0.0.1:6170");
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Failed to start VPN", e);
      Toast.makeText(context, "Failed to start VPN: " +
              (e.getMessage() != null ? e.getMessage() : "Unknown error"),
          Toast.LENGTH_SHORT).show();
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
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
      boolean allGranted = true;
      for (int result : grantResults) {
        if (result != PackageManager.PERMISSION_GRANTED) {
          allGranted = false;
          break;
        }
      }

      if (allGranted) {
        // 所有权限已授予
        startMyForegroundService();
      } else {
        Toast.makeText(this, "未授予必要权限，请检查设置", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void startMyForegroundService() {
    Intent serviceIntent = new Intent(this, MyAccessibilityService.class);
    ContextCompat.startForegroundService(this, serviceIntent);
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
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "onDestroy: Cleaning up resources", null);
    super.onDestroy();
    if (AutoJsUtil.scriptResultReceiver != null) {
      unregisterReceiver(AutoJsUtil.scriptResultReceiver);
      AutoJsUtil.scriptResultReceiver = null;
    }
    if (executorService != null) {
      executorService.shutdown();
    }
  }


  private void logInfo(String message) {
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", message, null);
  }

  private void logError(String message) {
    LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", message, null);
  }

  private void logWarn(String message) {
    LogFileUtil.logAndWrite(Log.WARN, "MainActivity", message, null);
  }

  private void showToast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }
}
