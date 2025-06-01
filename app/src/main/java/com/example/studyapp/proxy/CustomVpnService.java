package com.example.studyapp.proxy;

import static com.example.studyapp.utils.IpUtil.isValidIpAddress;
import static com.example.studyapp.utils.V2rayUtil.isV2rayRunning;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.studyapp.R;
import com.example.studyapp.config.ConfigLoader;
import com.example.studyapp.utils.SingboxUtil;
import com.example.studyapp.utils.V2rayUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;


public class CustomVpnService extends VpnService {


  private static final String TUN_ADDRESS = ConfigLoader.getTunnelAddress(); // TUN 的 IP 地址
  private static final int PREFIX_LENGTH = 28;           // 子网掩码

  private Thread vpnTrafficThread; // 保存线程引用
  private ParcelFileDescriptor vpnInterface;            // TUN 接口描述符

  private static CustomVpnService instance;

  private static final int NOTIFICATION_ID = 1;

  private volatile boolean isVpnActive = false; // 标志位控制数据包处理逻辑

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    isVpnActive = true; // 服务启动时激活
    // 开始前台服务
    startForeground(NOTIFICATION_ID, createNotification());
    try {
      // 检查 V2ray 是否已启动，避免重复进程

      SingboxUtil.startSingBox(getApplicationContext());
      // 启动 VPN 流量服务
      startVpn();
    } catch (Exception e) {
      Log.e("CustomVpnService", "Error in onStartCommand: " + e.getMessage(), e);
      stopSelf(); // 发生异常时停止服务
    }
    return START_STICKY;
  }

  private void startVpn() {
    try {
      // 配置虚拟网卡
      Builder builder = new Builder();

      // 不再需要手动验证 TUN_ADDRESS 和 PREFIX_LENGTH
      // 直接使用系统权限建立虚拟网卡，用于 TUN 接口和流量捕获
      builder.addAddress(TUN_ADDRESS, PREFIX_LENGTH); // 保证 TUN 接口 IP 地址仍与 v2ray 配置文件保持一致
      builder.addRoute("0.0.0.0", 0);                // 捕获所有 IPv4 流量

      // DNS 部分，如果有需要，也可以简化或直接保留 v2ray 配置提供的
      List<String> dnsServers = getSystemDnsServers();
      if (dnsServers.isEmpty()) {
        // 如果未能从系统中获取到 DNS 地址，添加备用默认值
        builder.addDnsServer("8.8.8.8"); // Google DNS
        builder.addDnsServer("8.8.4.4");
      } else {
        for (String dnsServer : dnsServers) {
          if (isValidIpAddress(dnsServer)) {
            builder.addDnsServer(dnsServer);
          }
        }
      }

      builder.setBlocking(true);
      // 直接建立 TUN 虚拟接口
      vpnInterface = builder.establish();
      if (vpnInterface == null) {
        Log.e(
            "CustomVpnService",
            "builder.establish() returned null. Check VpnService.Builder configuration and system state."
        );
        throw new IllegalStateException("VPN Interface establishment failed");
      }

      // 核心：启动流量转发服务，此后转发逻辑由 v2ray 接管
      new Thread(() -> handleVpnTraffic(vpnInterface)).start();

    } catch (Exception e) {
      // 增强日志描述信息，方便调试
      Log.e(
          "CustomVpnService",
          "startVpn failed: " + e.getMessage(),
          e
      );
    }
  }

  // 工具方法：判断 IP 地址是否合法
  private void handleVpnTraffic(ParcelFileDescriptor vpnInterface) {
    // 启动处理流量的线程
    vpnTrafficThread = new Thread(() -> {
      if (vpnInterface == null || !vpnInterface.getFileDescriptor().valid()) {
        Log.e("CustomVpnService", "ParcelFileDescriptor is invalid!");
        return;
      }

      byte[] packetData = new byte[32767]; // 数据包缓冲区

      try (FileInputStream inStream = new FileInputStream(vpnInterface.getFileDescriptor());
          FileOutputStream outStream = new FileOutputStream(vpnInterface.getFileDescriptor())) {

        while (!Thread.currentThread().isInterrupted() && isVpnActive) { // 检查线程是否已中断
          int length;
          try {
            length = inStream.read(packetData);
              if (length == -1) {
                  break; // 读取完成退出
              }
          } catch (IOException e) {
            Log.e("CustomVpnService", "Error reading packet", e);
            break; // 读取出错退出循环
          }

          if (length > 0) {
            boolean handled = processPacket(packetData, length);
            if (!handled) {
              outStream.write(packetData, 0, length); // 未处理的包写回
            }
          }
        }
      } catch (IOException e) {
        Log.e("CustomVpnService", "Error handling VPN traffic", e);
      } finally {
        try {
          vpnInterface.close();
        } catch (IOException e) {
          Log.e("CustomVpnService", "Failed to close vpnInterface", e);
        }
      }
    });
    vpnTrafficThread.start();
  }

  private boolean processPacket(byte[] packetData, int length) {
    if (!isVpnActive) {
      Log.w("CustomVpnService", "VPN is not active. Skipping packet processing.");
      return false;
    }
    if (packetData == null || length <= 0 || length > packetData.length) {
      Log.w("CustomVpnService", "Invalid packetData or length");
      return false;
    }

    try {
      boolean isTcpPacket = checkIfTcpPacket(packetData);
      boolean isDnsPacket = checkIfDnsRequest(packetData);
      Log.d("CustomVpnService", "Packet Info: TCP=" + isTcpPacket + ", DNS=" + isDnsPacket + ", Length=" + length);

      if (isTcpPacket || isDnsPacket) {
        Log.i("CustomVpnService", "Forwarding to V2Ray. Packet Length: " + length);
        return true;
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      Log.e("CustomVpnService", "Malformed packet data: out of bounds", e);
    } catch (IllegalArgumentException e) {
      Log.e("CustomVpnService", "Invalid packet content", e);
    } catch (Exception e) {
      Log.e("CustomVpnService", "Unexpected error during packet processing. Packet Length: " + length, e);
    }
    return false;
  }

  @Override
  public boolean stopService(Intent name) {
    isVpnActive = false; // 服务停止时停用
    super.stopService(name);

    // 停止处理数据包的线程
    if (vpnTrafficThread != null && vpnTrafficThread.isAlive()) {
      vpnTrafficThread.interrupt(); // 中断线程
      try {
        vpnTrafficThread.join(); // 等待线程停止
      } catch (InterruptedException e) {
        Log.e("CustomVpnService", "Error while stopping vpnTrafficThread", e);
        Thread.currentThread().interrupt(); // 重新设置当前线程的中断状态
      }
      vpnTrafficThread = null; // 清空线程引用
    }

    // 关闭 VPN 接口
    if (vpnInterface != null) {
      try {
        vpnInterface.close();
      } catch (IOException e) {
        Log.e("CustomVpnService", "Error closing VPN interface: " + e.getMessage(), e);
      }
      vpnInterface = null; // 避免资源泄露
    }

    // 停止 V2Ray 服务
    SingboxUtil.stopSingBox();
    Log.i("CustomVpnService", "VPN 服务已停止");
    return true;
  }

  @Override
  public void onDestroy() {
    isVpnActive = false; // 服务销毁时停用
    super.onDestroy();

    // 停止处理数据包的线程
    if (vpnTrafficThread != null && vpnTrafficThread.isAlive()) {
      vpnTrafficThread.interrupt(); // 中断线程
      try {
        vpnTrafficThread.join(); // 等待线程停止
      } catch (InterruptedException e) {
        Log.e("CustomVpnService", "Error while stopping vpnTrafficThread", e);
        Thread.currentThread().interrupt(); // 重新设置当前线程的中断状态
      }
      vpnTrafficThread = null; // 清空线程引用
    }

    // 关闭 VPN 接口
    if (vpnInterface != null) {
      try {
        vpnInterface.close();
      } catch (IOException e) {
        Log.e("CustomVpnService", "Error closing VPN interface: " + e.getMessage(), e);
      }
      vpnInterface = null; // 避免资源泄露
    }

    // 停止 V2Ray 服务
    SingboxUtil.stopSingBox();
    Log.i("CustomVpnService", "VPN 服务已销毁");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this; // 在创建时将实例存储到静态字段
  }

  private boolean checkIfTcpPacket(byte[] packetData) {
    // IPv4 数据包最前面 1 字节的前 4 位是版本号
    int version = (packetData[0] >> 4) & 0xF;
    if (version != 4) {
      return false; // 非 IPv4，不处理
    }

    // IPv4 中 Protocol 字段位于位置 9，值为 6 表示 TCP 协议
    int protocol = packetData[9] & 0xFF; // 取无符号位
    return protocol == 6; // 6 表示 TCP
  }

  private boolean checkIfDnsRequest(byte[] packetData) {
    // IPv4 UDP 协议号为 17
    int protocol = packetData[9] & 0xFF;
    if (protocol != 17) {
      return false; // 不是 UDP
    }

    // UDP 源端口在 IPv4 头部后的第 0-1 字节（总长度 IPv4 Header 20 字节 + UDP Offset）
    // IPv4 头长度在第一个字节后四位
    int ipHeaderLength = (packetData[0] & 0x0F) * 4;
    int sourcePort = ((packetData[ipHeaderLength] & 0xFF) << 8) | (packetData[ipHeaderLength + 1] & 0xFF);
    int destPort = ((packetData[ipHeaderLength + 2] & 0xFF) << 8) | (packetData[ipHeaderLength + 3] & 0xFF);

    // 检查是否是 UDP 的 DNS 端口 (53)
    return sourcePort == 53 || destPort == 53;
  }

  private List<String> getSystemDnsServers() {
    List<String> dnsServers = new ArrayList<>();
    try {
      String command = "getprop | grep dns";
      Process process = Runtime.getRuntime().exec(command);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains("dns") && line.contains(":")) {
            String dns = line.split(":")[1].trim();
            if (isValidIpAddress(dns)) { // 添加有效性检查
              dnsServers.add(dns);
            }
          }
        }
      }
    } catch (IOException e) {
      // 捕获问题日志
      Log.e("CustomVpnService", "Error while fetching DNS servers: " + e.getMessage(), e);
    }
    // 添加默认 DNS 在无效情况下
    if (dnsServers.isEmpty()) {
      dnsServers.add("8.8.8.8");
      dnsServers.add("8.8.4.4");
      dnsServers.add("1.1.1.1");
    }
    return dnsServers;
  }

  private Notification createNotification() {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
          "vpn_service",
          "VPN Service",
          NotificationManager.IMPORTANCE_DEFAULT
      );
      notificationManager.createNotificationChannel(channel);
    }
    return new NotificationCompat.Builder(this, "vpn_service")
        .setContentTitle("VPN 服务")
        .setContentText("VPN 正在运行...")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .build();
  }

  public static CustomVpnService getInstance() {
    return instance;
  }
}
