package com.example.studyapp;

import static com.example.studyapp.task.TaskUtil.infoUpload;

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
import android.text.TextUtils;
import android.util.Log;
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

import com.example.studyapp.proxy.ClashUtil;
import com.example.studyapp.service.MyAccessibilityService;
import com.example.studyapp.task.TaskUtil;
import com.example.studyapp.utils.LogFileUtil;
import com.example.studyapp.utils.ShellUtils;
import com.example.studyapp.worker.CheckAccessibilityWorker;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

  private static WeakReference<MainActivity> instance;

  private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

  private static final int ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE = 1001;

  public static ExecutorService executorService;

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

  public static volatile String scriptResult;

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

  /**
   * 获取 Android 设备的 ANDROID_ID
   *
   * @param context 应用上下文
   * @return 设备的 ANDROID_ID，若无法获取，则返回 null
   */
  private String getAndroidId(Context context) {
    if (context == null) {
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "getAndroidId: Context cannot be null",null);
      throw new IllegalArgumentException("Context cannot be null");
    }
    try {
      return Settings.Secure.getString(
          context.getContentResolver(),
          Settings.Secure.ANDROID_ID
      );
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "getAndroidId: Failed to get ANDROID_ID",e);
      return null;
    }
  }


  private static final int REQUEST_CODE_PERMISSIONS = 100;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogFileUtil.initialize(this);
    setContentView(R.layout.activity_main);
    instance = new WeakReference<>(this);

    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "onCreate: Initializing application",null);
    initializeExecutorService();
    System.setProperty("java.library.path", this.getApplicationInfo().nativeLibraryDir);
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
      Toast.makeText(this, "Network is not available", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "Network not available, closing app.",null);
      finish();
    }

    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "onCreate: Setting up work manager",null);
    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(CheckAccessibilityWorker.class, 15, TimeUnit.MINUTES)
        .build();
    WorkManager.getInstance(this).enqueue(workRequest);

    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "onCreate: Setting up UI components",null);
    Button runScriptButton = findViewById(R.id.run_script_button);
    if (runScriptButton != null) {
      runScriptButton.setOnClickListener(v -> AutoJsUtil.runAutojsScript(this));
    } else {
      LogFileUtil.logAndWrite(Log.WARN, "MainActivity", "Run Script Button not found",null);
      Toast.makeText(this, "Button not found", Toast.LENGTH_SHORT).show();
    }

    Button connectButton = findViewById(R.id.connectVpnButton);
    if (connectButton != null) {
      connectButton.setOnClickListener(v -> {
        String chmodResult = ShellUtils.execRootCmdAndGetResult("pm uninstall com.rovio.baba");
      });
//      connectButton.setOnClickListener(v -> startProxyVpn(this));
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
      modifyDeviceInfoButton.setOnClickListener(v -> ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this));
    } else {
      Toast.makeText(this, "modifyDeviceInfo button not found", Toast.LENGTH_SHORT).show();
    }

    Button resetDeviceInfoButton = findViewById(R.id.resetDeviceInfoButton);
    if (resetDeviceInfoButton != null) {
      resetDeviceInfoButton.setOnClickListener(v -> ChangeDeviceInfoUtil.resetChangedDeviceInfo(getPackageName(), this));
    } else {
      Toast.makeText(this, "resetDeviceInfo button not found", Toast.LENGTH_SHORT).show();
    }

    // 初始化 ChangeDeviceInfoUtil
    String androidId = getAndroidId(this);
    String taskId = UUID.randomUUID().toString();
//    ChangeDeviceInfoUtil.initialize("US", 2, this, androidId);
    // 获取输入框和按钮
    Button executeButton = findViewById(R.id.execute_button);
    Button stopExecuteButton = findViewById(R.id.stop_execute_button);

    // 设置按钮的点击事件
    if (executeButton != null) {
      executeButton.setOnClickListener(v -> {
        executeButton.setEnabled(false);
        Toast.makeText(this, "任务正在执行", Toast.LENGTH_SHORT).show();
        executeLogic(androidId,taskId);
      });
    }
    if (stopExecuteButton != null) {
      stopExecuteButton.setOnClickListener(v -> {
        if (executorService != null && !executorService.isShutdown()) {
          executorService.shutdownNow();
          ClashUtil.stopProxy(this);
          AutoJsUtil.stopAutojsScript(this);
          executeButton.setEnabled(true);
        }
      });
    } else {
      Toast.makeText(this, "Stop button not found", Toast.LENGTH_SHORT).show();
    }
  }

  private void executeLogic(String androidId, String taskId) {
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeLogic: Start execution",null);

    if (!isNetworkAvailable(this)) {
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "executeLogic: Network is not available!",null);
      Toast.makeText(this, "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show();
      return;
    }

    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeLogic: Submitting job to executor",null);
    initializeExecutorService();
    ChangeDeviceInfoUtil.getDeviceInfo(taskId, androidId);
    executeSingleLogic();
    executorService.submit(() -> {
      try {
        AutoJsUtil.registerScriptResultReceiver(this);
        AutoJsUtil.flag = true;

        while (isRunning) {
          if (!isRunning) break;

          // 从队列中获取最新的 scriptResult
          LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Running AutoJs script",null);
          String currentScriptResult = scriptResultQueue.take();
          ChangeDeviceInfoUtil.getDeviceInfo(taskId, androidId);
          ChangeDeviceInfoUtil.processPackageInfo(TaskUtil.getPackageInfo(androidId), this);
          executeSingleLogic();
          TaskUtil.execSaveTask(this, androidId, taskId, currentScriptResult);
          LogFileUtil.logAndWrite(android.util.Log.DEBUG, "MainActivity", "----发送result------;" + currentScriptResult, null);
          if (currentScriptResult != null && !TextUtils.isEmpty(currentScriptResult)) {
            infoUpload(this, androidId, currentScriptResult);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "executeLogic: Thread interrupted while waiting", e);
      } catch (Exception e) {
        LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "executeLogic: Unexpected task error.", e);
      }
    });
  }

  public static final LinkedBlockingQueue<String> scriptResultQueue = new LinkedBlockingQueue<>();
  private volatile boolean isRunning = true; // 主线程运行状态
  public static final Object taskLock = new Object();      // 任务逻辑锁

  public void executeSingleLogic() {
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Proxy not active, starting VPN",null);
    startProxyVpn(this);
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Changing device info",null);
    ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this);
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Running AutoJs script",null);
    AutoJsUtil.runAutojsScript(this);
  }


  private void startProxyVpn(Context context) {
    if (!isNetworkAvailable(context)) {
      Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Network is not available.",null);
      return;
    }

    if (!(context instanceof Activity)) {
      Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Context is not an Activity.",null);
      return;
    }

    try {
      ClashUtil.startProxy(context); // 在主线程中调用
      ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170");
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Failed to start VPN",e);
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
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "onDestroy: Cleaning up resources",null);
    super.onDestroy();
    instance.clear();
    if (AutoJsUtil.scriptResultReceiver != null) {
      unregisterReceiver(AutoJsUtil.scriptResultReceiver);
      AutoJsUtil.scriptResultReceiver = null;
    }
    if (executorService != null) {
      executorService.shutdown();
    }
    isRunning = false;
  }

  public static MainActivity getInstance() {
    return instance.get(); // 返回实例
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
