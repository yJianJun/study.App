package com.example.retention.worker;

import static com.example.retention.utils.Utils.isNetworkAvailable;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.CoroutineWorker;
import androidx.work.WorkerParameters;

import com.example.retention.MainActivity;
import com.example.retention.autoJS.AutoJsUtil;
import com.example.retention.config.CountryCode;
import com.example.retention.device.ChangeDeviceInfoUtil;
import com.example.retention.proxy.ClashUtil;
import com.example.retention.utils.LogFileUtil;
import com.example.retention.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import kotlin.coroutines.Continuation;

public class LoadDeviceWorker extends CoroutineWorker {

  private String androidId = "FyZqWrStUvOpKlMn";
  private Context context;

  public LoadDeviceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    this.context = context;
  }

  @Override
  public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
    String taskId = UUID.randomUUID().toString();
    boolean result = ChangeDeviceInfoUtil.getDeviceInfoSync(taskId, androidId);
    String packageName = ChangeDeviceInfoUtil.packageName;
    String zipName = ChangeDeviceInfoUtil.zipName;
    LogFileUtil.logAndWrite(Log.INFO, "TAG","doWork: " + result + " " + packageName + " " + zipName, null);
    if (result && !TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(zipName)) {
      boolean isSuccess = ChangeDeviceInfoUtil.processPackageInfoWithDeviceInfo(packageName, zipName, getApplicationContext(), androidId, taskId);
      if (isSuccess) {
        executeSingleLogic(context, packageName);
      }
    } else {
      LogFileUtil.logAndWrite(Log.INFO, "TAG", "doWork: get Device info false",  null);
    }
    return Result.success();
  }

  public void executeSingleLogic(Context context, String packageName) {
    if (packageName == null || packageName.isEmpty()){
      LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Package name is empty", null);
      return;
    }
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Proxy not active, starting VPN", null);
    startProxyVpn(context);
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Changing device info", null);
    ChangeDeviceInfoUtil.changeDeviceInfo(packageName, context, MainActivity.armClient);
    LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Running AutoJs script", null);
    Utils.writePackageName(packageName);
    AutoJsUtil.runAutojsScript(context);
  }

  private void startProxyVpn(Context context) {
    if (!isNetworkAvailable(context)) {
      Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
      LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Network is not available.", null);
    }

//        if (!(context instanceof Activity)) {
//            Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
//            LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Context is not an Activity.",null);
//            return;
//        }
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
}
