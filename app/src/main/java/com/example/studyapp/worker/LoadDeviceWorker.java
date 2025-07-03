package com.example.studyapp.worker;

import static com.example.studyapp.utils.Utils.isNetworkAvailable;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.CoroutineWorker;
import androidx.work.WorkerParameters;

import com.example.studyapp.autoJS.AutoJsUtil;
import com.example.studyapp.device.ChangeDeviceInfoUtil;
import com.example.studyapp.proxy.ClashUtil;
import com.example.studyapp.task.TaskUtil;
import com.example.studyapp.utils.LogFileUtil;
import com.example.studyapp.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
        Log.d("TAG", "doWork: "+result+" "+packageName+" "+zipName);
        if (result && !TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(zipName)){
            boolean isSuccess = ChangeDeviceInfoUtil.processPackageInfoWithDeviceInfo(packageName,zipName, getApplicationContext(), androidId, taskId);
            if (isSuccess){
                executeSingleLogic(context);
            }
        }else {
            Log.d("TAG", "doWork: get Device info false");
        }
        return Result.success();
    }

    public void executeSingleLogic(Context context) {
        LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Proxy not active, starting VPN",null);
        if (!startProxyVpn(context)){
            return;
        }
        LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Changing device info",null);
        ChangeDeviceInfoUtil.changeDeviceInfo(context.getPackageName(), context);
        LogFileUtil.logAndWrite(Log.INFO, "MainActivity", "executeSingleLogic: Running AutoJs script",null);
        Utils.writePackageName(ChangeDeviceInfoUtil.packageName);
        AutoJsUtil.runAutojsScript(context);
    }

    private boolean startProxyVpn(Context context) {
        if (!isNetworkAvailable(context)) {
            Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show();
            LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Network is not available.",null);
            return false;
        }

//        if (!(context instanceof Activity)) {
//            Toast.makeText(context, "Context must be an Activity", Toast.LENGTH_SHORT).show();
//            LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Context is not an Activity.",null);
//            return;
//        }

        try {
            ClashUtil.startProxy(context); // 在主线程中调用
            ClashUtil.switchProxyGroup("GLOBAL", "us", "http://127.0.0.1:6170");
            return ClashUtil.checkCountryIsUS();
        } catch (Exception e) {
            LogFileUtil.logAndWrite(Log.ERROR, "MainActivity", "startProxyVpn: Failed to start VPN",e);
            Toast.makeText(context, "Failed to start VPN: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
