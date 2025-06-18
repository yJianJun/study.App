package com.example.studyapp.service;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.example.studyapp.MainActivity;
import com.example.studyapp.R;

public class MyAccessibilityService extends AccessibilityService {


  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {

  }

  @Override
  public void onInterrupt() {

  }

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    startForegroundService();
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
          "2",
          "无障碍服务",
          NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("此服务在后台运行，提供无障碍支持。");
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
  }

  private void startForegroundService() {
    createNotificationChannel();

    Intent notificationIntent = new Intent(this, MainActivity.class);
    int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags);

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, "2")
        .setContentTitle("无障碍服务运行中")
        .setContentText("此服务正在持续运行")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(pendingIntent);

    Notification notification = notificationBuilder.build();

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
        != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(this, "请授予前台服务权限以启动服务", Toast.LENGTH_SHORT).show();
      return;
    }

    String[] requiredPermissions = {
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
        "android.permission.CAPTURE_VIDEO_OUTPUT"
    };

    for (String permission : requiredPermissions) {
      if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        return;
      }
    }

    // 设置前台服务类型（仅在 API 29+）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    } else {
      startForeground(1, notification);
    }
  }
}
