package com.example.studyapp.autoJS;

import static com.example.studyapp.task.TaskUtil.downloadCodeFile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.studyapp.MainActivity;
import com.example.studyapp.utils.LogFileUtil;
import com.example.studyapp.utils.ShellUtils;

import java.io.File;

public class AutoJsUtil {

  public static BroadcastReceiver scriptResultReceiver;
  public static volatile boolean flag;

  private static int count;

  public static void runAutojsScript(Context context) {
    // 检查脚本文件
    LogFileUtil.logAndWrite(android.util.Log.INFO, "AutoJsUtil", "-------脚本运行开始：--------" + count++,null);
    File scriptDir = new File(Environment.getExternalStorageDirectory(), "script");//todo
    scriptDir.delete();
    File scriptFile = downloadCodeFile("mainold.js", scriptDir);//todo
    if (scriptFile == null || !scriptFile.exists()) {
      runOnUiThread(() -> Toast.makeText(context, "下载脚本文件失败", Toast.LENGTH_SHORT).show());
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "AutoJsUtil", "下载脚本文件失败",null);
      return;
    }

    // 检查是否安装 Auto.js
    if (!isAppInstalled("org.autojs.autojs6")) {
      runOnUiThread(() -> Toast.makeText(context, "Auto.js app not installed", Toast.LENGTH_SHORT).show());
      return;
    }

    // 启动 AutoJs
    Intent intent = new Intent();
    intent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.RunIntentActivity");
    intent.putExtra("path", scriptFile.getAbsolutePath());
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
      LogFileUtil.logAndWrite(android.util.Log.INFO, "AutoJsUtil", "脚本运行中：" + scriptFile.getAbsolutePath(),null);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "AutoJsUtil", "运行脚本失败",e);
      runOnUiThread(() -> Toast.makeText(context, "运行脚本失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
  }

  public static void registerScriptResultReceiver(Context context) {
    if (scriptResultReceiver == null) {
      // 创建广播接收器
      scriptResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          LogFileUtil.logAndWrite(android.util.Log.DEBUG, "MainActivity", "----脚本运行结束通知一次------; 当前线程：" + Thread.currentThread().getName(), null);
          String scriptResult = intent.getStringExtra(SCRIPT_RESULT_KEY);
          try {
            MainActivity.scriptResultQueue.put(scriptResult); // 将结果加入队列
            LogFileUtil.logAndWrite(android.util.Log.DEBUG, "MainActivity", "----收到result------;" + scriptResult, null);
//            AutoJsUtil.flag = true; // 唤醒
//            taskLock.notifyAll();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 处理中断
          }
        }
      };

      // 注册广播接收器
      try {
        IntentFilter filter = new IntentFilter(AUTOJS_SCRIPT_FINISHED_ACTION);
        Context appContext = context.getApplicationContext();
        ContextCompat.registerReceiver(appContext, scriptResultReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        LogFileUtil.logAndWrite(android.util.Log.DEBUG, "MainActivity", "广播接收器成功注册",null);
      } catch (Exception e) {
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "MainActivity", "Failed to register receiver",e);
        scriptResultReceiver = null; // 确保状态一致
      }
    } else {
      LogFileUtil.logAndWrite(android.util.Log.WARN, "MainActivity", "广播接收器已注册，无需重复注册",null);
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
  public static boolean isAppInstalled(String packageName) {
    LogFileUtil.logAndWrite(android.util.Log.DEBUG, "isAppInstalled", "Checking if app is installed: " + packageName,null);

    // 通过 Shell 命令实现检测
    try {
      String cmd = "pm list packages | grep " + packageName;
      String result = ShellUtils.execRootCmdAndGetResult(cmd);

      if (result != null && result.contains(packageName)) {
        LogFileUtil.logAndWrite(android.util.Log.DEBUG, "isAppInstalled", "App is installed: " + packageName,null);
        return true;
      } else {
        LogFileUtil.logAndWrite(android.util.Log.WARN, "isAppInstalled", "App not installed: " + packageName,null);
        return false;
      }
    } catch (Exception e) {
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "isAppInstalled", "Unexpected exception while checking app installation: " + packageName,e);
      return false;
    }
  }

  private static final String AUTOJS_SCRIPT_FINISHED_ACTION = "org.autojs.SCRIPT_FINISHED_CACHE";
  private static final String SCRIPT_RESULT_KEY = "result";

  public static void stopAutojsScript(Context context) {
    // 停止运行脚本的 Intent
    Intent stopIntent = new Intent();
    stopIntent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity");
    stopIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    // 检查目标活动是否存在
    boolean activityAvailable = isActivityAvailable(context, "org.autojs.autojs6", "org.autojs.autojs.external.open.StopServiceActivity");
    LogFileUtil.logAndWrite(android.util.Log.DEBUG, "AutoJsUtil", "是否找到目标活动: " + activityAvailable,null);

    if (activityAvailable) {
      try {
        context.startActivity(stopIntent); // 尝试发送停止脚本的 Intent
        Toast.makeText(context, "脚本停止命令已发送", Toast.LENGTH_SHORT).show();
      } catch (Exception e) {
        Toast.makeText(context, "无法发送停止命令，请检查 AutoJs 配置", Toast.LENGTH_SHORT).show();
        LogFileUtil.logAndWrite(android.util.Log.ERROR, "AutoJsUtil", "发送停止命令时发生错误",e);
      }
    } else {
      Toast.makeText(context, "目标活动未找到或已更改，请检查 AutoJs 配置", Toast.LENGTH_LONG).show();
      LogFileUtil.logAndWrite(android.util.Log.ERROR, "AutoJsUtil", "目标活动未找到: org.autojs.autojs.external.open.StopServiceActivity",null);
    }
  }
}