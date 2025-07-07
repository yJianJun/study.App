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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
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

import androidx.annotation.IdRes;
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
import com.example.studyapp.utils.IpUtil;
import com.example.studyapp.utils.LogFileUtil;
import com.example.studyapp.worker.CheckAccessibilityWorker;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

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

  private static final int REQUEST_CODE_PERMISSIONS = 100;

  private static final String TAG = "MainActivity";
  private static final String PACKAGE_SCHEME = "package:";
  private static final int DEVICE_TYPE = 2;

  // 定义支持的国家代码常量
  private static final class CountryCode {

    static final String US = "us";
    static final String RU = "ru";
    // 默认使用美国
    static final String DEFAULT = US;
  }

  // 当前使用的国家代码
  private String currentCountry = CountryCode.DEFAULT;

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
    return "FyZqWrStUvOpKlMn";
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initializeBasicComponents();
    checkAndRequestPermissions();
    checkNetworkConnection();
    setupWorkManager();
    initializeButtons();
  }

  private void initializeBasicComponents() {
    LogFileUtil.initialize(this);
    setContentView(R.layout.activity_main);
    instance = new WeakReference<>(this);
    LogFileUtil.logAndWrite(Log.INFO, TAG, "onCreate: Initializing application", null);
    initializeExecutorService();
    System.setProperty("java.library.path", getApplicationInfo().nativeLibraryDir);
  }

  private void checkAndRequestPermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      checkStoragePermission();
    } else {
      checkAllFilesAccessPermission();
    }
  }

  private void checkStoragePermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this,
          new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
          REQUEST_CODE_STORAGE_PERMISSION
      );
    }
  }

  private void checkAllFilesAccessPermission() {
    if (VERSION.SDK_INT >= VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse(PACKAGE_SCHEME + getPackageName()));
        startActivityForResult(intent, ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE);
      }
    }
  }

  private void checkNetworkConnection() {
    if (!isNetworkAvailable(this)) {
      LogFileUtil.logAndWrite(Log.ERROR, TAG, "Network not available, closing app.", null);
      Toast.makeText(this, R.string.network_unavailable, Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  private void setupWorkManager() {
    LogFileUtil.logAndWrite(Log.INFO, TAG, "onCreate: Setting up work manager", null);
    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
        CheckAccessibilityWorker.class,
        15,
        TimeUnit.MINUTES
    ).build();
    WorkManager.getInstance(this).enqueue(workRequest);
  }

  // 添加切换国家的方法
  private String switchCountry() {
    currentCountry = currentCountry.equals(CountryCode.US) ?
        CountryCode.RU : CountryCode.US;
    LogFileUtil.logAndWrite(Log.INFO, TAG,
        "Switched country to: " + currentCountry, null);
    return currentCountry;
  }


  private void initializeButtons() {
    LogFileUtil.logAndWrite(Log.INFO, TAG, "onCreate: Setting up UI components", null);
    String androidId = getAndroidId(this);
    String taskId = UUID.randomUUID().toString();

    setupButton(R.id.run_script_button, v -> executeRunScript(androidId));
    setupButton(R.id.connectVpnButton, v -> startProxyVpn(this));
    setupButton(R.id.disconnectVpnButton, v -> ClashUtil.stopProxy(this));
    setupButton(R.id.switchVpnButton, v -> {
      ClashUtil.switchProxyGroup("GLOBAL", switchCountry(), "http://127.0.0.1:6170");
    });
    setupButton(R.id.resetDeviceInfoButton,
        v -> ChangeDeviceInfoUtil.resetChangedDeviceInfo(getPackageName(), this));
    setupExecutionButtons(androidId, taskId);
  }


  private void setupButton(@IdRes int buttonId, View.OnClickListener listener) {
    Button button = findViewById(buttonId);
    if (button != null) {
      button.setOnClickListener(listener);
    } else {
      LogFileUtil.logAndWrite(Log.WARN, TAG, "Button not found: " + buttonId, null);
      Toast.makeText(this, R.string.button_not_found, Toast.LENGTH_SHORT).show();
    }
  }

  private void executeRunScript(String androidId) {
    initializeExecutorService();
    executorService.submit(() -> {
      try {
        TaskUtil.infoUpload(MainActivity.this, androidId, "wsj.reader_sp");
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void setupExecutionButtons(String androidId, String taskId) {
    Button executeButton = findViewById(R.id.execute_button);
    Button stopExecuteButton = findViewById(R.id.stop_execute_button);

    if (executeButton != null) {
      executeButton.setOnClickListener(v -> {
        executeButton.setEnabled(false);
        Toast.makeText(this, R.string.task_executing, Toast.LENGTH_SHORT).show();
        executeLogic(androidId, taskId);
      });
    }

    setupStopExecutionButton(stopExecuteButton, executeButton);
  }

  private void setupStopExecutionButton(Button stopExecuteButton, Button executeButton) {
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
      Toast.makeText(this, R.string.stop_button_not_found, Toast.LENGTH_SHORT).show();
    }
  }

  private void executeLogic(String androidId, String taskId) {
    LogFileUtil.logAndWrite(Log.INFO, TAG, "Start execution", null);

    if (!isNetworkAvailable(this)) {
      return;
    }

    initializeExecutorService();
    executorService.submit(() -> processMainTask(androidId, taskId));
  }

  private void processMainTask(String androidId, String taskId) {
    try {
      initializeDeviceAndRunScript();
      processScriptResults(androidId, taskId);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, TAG, "Unexpected task error", e);
    }
  }

  private void initializeDeviceAndRunScript() {
    ChangeDeviceInfoUtil.getAddDeviceInfo(currentCountry.toUpperCase(), DEVICE_TYPE,
        (bigoDevice, afDevice) -> {
          startProxyVpn(this);
          ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this,
              bigoDevice, afDevice);
          AutoJsUtil.runAutojsScript(this);
        });
    AutoJsUtil.registerScriptResultReceiver(this);
  }


  private void processScriptResults(String androidId, String taskId) {
    while (isRunning) {
      try {
        String currentScriptResult = scriptResultQueue.take();
        if (!isRunning) {
          break;
        }

        processScriptResult(androidId, taskId, currentScriptResult);
        LogFileUtil.logAndWrite(Log.DEBUG, TAG, "Script result processed: " + currentScriptResult, null);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LogFileUtil.logAndWrite(Log.ERROR, TAG, "Thread interrupted while waiting", e);
        break;
      } catch (Exception e) {
        LogFileUtil.logAndWrite(Log.ERROR, TAG, "Error processing script result", e);
      }
    }
  }

  private void processScriptResult(String androidId, String taskId, String result) {

    ChangeDeviceInfoUtil.getAddDeviceInfo(currentCountry.toUpperCase(), DEVICE_TYPE,
        (bigoDevice, afDevice) -> {
          try {
            updateDeviceAndExecuteTask(androidId, taskId, result, bigoDevice, afDevice);
          } catch (Exception e) {
            LogFileUtil.logAndWrite(Log.ERROR, TAG, "Device info change error", e);
          }
        });
  }


  private void updateDeviceAndExecuteTask(String androidId, String taskId,
      String result, JSONObject bigoDevice, JSONObject afDevice) {
    startProxyVpn(this);
    ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this, bigoDevice, afDevice);
    AutoJsUtil.runAutojsScript(this);

    TaskUtil.execSaveTask(this, androidId, taskId, result, IpUtil.fetchGeoInfo());
    try {
      infoUpload(this, androidId, result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  public static final LinkedBlockingQueue<String> scriptResultQueue = new LinkedBlockingQueue<>(1);
  private volatile boolean isRunning = true; // 主线程运行状态
  public static final Object taskLock = new Object();      // 任务逻辑锁


  private void startProxyVpn(Context context) {
    if (!isNetworkAvailable(context)) {
      Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Network is not available.", null);
    }

    if (!(context instanceof Activity)) {
      Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Context is not an Activity.", null);
    }
    try {
      ClashUtil.startProxy(context);
      ClashUtil.switchProxyGroup("GLOBAL", switchCountry(), "http://127.0.0.1:6170");
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, TAG, "startProxyVpn: Failed to start VPN", e);
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
    instance.clear();
    if (AutoJsUtil.scriptResultReceiver != null) {
      unregisterReceiver(AutoJsUtil.scriptResultReceiver);
      AutoJsUtil.scriptResultReceiver = null;
    }
    if (executorService != null) {
      executorService.shutdown();
    }
    isRunning = false;
    synchronized (taskLock) {
      taskLock.notifyAll();
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
