package com.example.studyapp.service;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import android.view.accessibility.AccessibilityEvent;
import androidx.core.app.NotificationCompat;
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
      String channelId = "2";
      String channelName = "Foreground Service";
      int importance = NotificationManager.IMPORTANCE_LOW;
      NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
      NotificationManager notificationManager = getSystemService(NotificationManager.class);
      if (notificationManager != null) {
        notificationManager.createNotificationChannel(channel);
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
    android.app.Notification notification = notificationBuilder.build();
    startForeground(1, notification);
  }
}
