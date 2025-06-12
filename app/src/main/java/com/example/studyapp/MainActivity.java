package com.example.studyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
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
import android.widget.EditText;
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
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {

  private static WeakReference<MainActivity> instance;

  private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

  private static final int ALLOW_ALL_FILES_ACCESS_PERMISSION_CODE = 1001;

  public ExecutorService executorService;

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

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    instance = new WeakReference<>(this);

    initializeExecutorService();
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
      runScriptButton.setOnClickListener(v -> AutoJsUtil.runAutojsScript(this,""));
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
    ChangeDeviceInfoUtil.initialize("US", 2);
    // 获取输入框和按钮
    EditText inputNumber = findViewById(R.id.input_number);
    Button executeButton = findViewById(R.id.execute_button);
    Button stopExecuteButton = findViewById(R.id.stop_execute_button);

    // 设置按钮的点击事件
    if (inputNumber != null && executeButton != null) {
      executeButton.setOnClickListener(v -> {
        executeButton.setEnabled(false);
        Toast.makeText(this, "任务正在执行", Toast.LENGTH_SHORT).show();
        executeLogic(inputNumber);
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

  private void executeLogic(EditText inputNumber) {
    Log.i("MainActivity", "executeLogic: Start execution");

    if (inputNumber == null) {
      Log.e("MainActivity", "executeLogic: Input box is null!");
      Toast.makeText(this, "输入框为空", Toast.LENGTH_SHORT).show();
      return;
    }

    String numberText = inputNumber.getText().toString().trim();
    if (TextUtils.isEmpty(numberText)) {
      Log.e("MainActivity", "executeLogic: No number input provided!");
      Toast.makeText(this, "请输入一个数字", Toast.LENGTH_SHORT).show();
      return;
    }

    int number;
    try {
      number = Integer.parseInt(numberText);
      if (number < 1 || number > 1000) {
        Log.e("MainActivity", "executeLogic: Invalid number range: " + number);
        Toast.makeText(this, "请输入 1 到 1000 之间的数字", Toast.LENGTH_SHORT).show();
        return;
      }
    } catch (NumberFormatException e) {
      Log.e("MainActivity", "executeLogic: Invalid number format: " + numberText, e);
      Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
      return;
    }

    if (!isNetworkAvailable(this)) {
      Log.e("MainActivity", "executeLogic: Network is not available!");
      Toast.makeText(this, "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show();
      return;
    }

    Log.i("MainActivity", "executeLogic: Submitting job to executor");
    initializeExecutorService();
    executorService.submit(() -> {
      try {
        AutoJsUtil.flag = true;
        for (int i = 0; i < number; i++) {
          synchronized (lock) {
            // 等待 flag 设置为 false 时暂停
            while (!AutoJsUtil.flag) {
              lock.wait(); // 当前线程进入等待状态
            }
            // 执行实际逻辑
            executeSingleLogic(i);
          }
        }
      } catch (InterruptedException e) {
        Log.e("MainActivity", "executeLogic: Thread interrupted while waiting", e);
      } catch (Exception e) {
        Log.e("MainActivity", "executeLogic: Unexpected task error.", e);
      }
    });
  }

  public final static Object lock = new Object();

  public void executeSingleLogic(int i) {
    Log.i("MainActivity", "executeSingleLogic: Start execution for index " + i);
    long startTime = System.currentTimeMillis(); // 开始计时

    Log.i("MainActivity", "executeSingleLogic: Proxy not active, starting VPN");
    startProxyVpn(this);

    Log.i("MainActivity", "executeSingleLogic: Switching proxy group to " + proxyNames[i]);
    try {
      ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170");
    } catch (Exception e) {
      Log.e("MainActivity", "executeSingleLogic: Failed to switch proxy group", e);
      runOnUiThread(() -> Toast.makeText(this, "切换代理组失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    Log.i("MainActivity", "executeSingleLogic: Changing device info");
    String url = ChangeDeviceInfoUtil.changeDeviceInfo(getPackageName(), this);

    Log.i("MainActivity", "executeSingleLogic: Running AutoJs script");
    AutoJsUtil.runAutojsScript(this, url);

    runOnUiThread(() -> Toast.makeText(this, "第 " + (i + 1) + " 次执行完成", Toast.LENGTH_SHORT).show());

    long endTime = System.currentTimeMillis(); // 结束计时
    Log.i("MainActivity", "executeSingleLogic: Finished execution for index " + i + " in " + (endTime - startTime) + " ms");
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
    instance.clear();
    if (AutoJsUtil.scriptResultReceiver != null) {
      unregisterReceiver(AutoJsUtil.scriptResultReceiver);
    }
    if (executorService != null) {
      executorService.shutdown(); // 关闭线程池
    }
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
