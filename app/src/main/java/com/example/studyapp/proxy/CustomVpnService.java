package com.example.studyapp.proxy;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

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


    private static final String TUN_ADDRESS = "172.19.0.1"; // TUN 的 IP 地址
    private static final int PREFIX_LENGTH = 28;           // 子网掩码
    private static final int MAX_RETRY = 5;

    private ParcelFileDescriptor vpnInterface;            // TUN 接口描述符

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // 检查 V2ray 是否已启动，避免重复进程
            if (!isV2rayRunning()) {
                V2rayUtil.startV2Ray(getApplicationContext());
            } else {
                Log.w("CustomVpnService", "V2Ray already running, skipping redundant start.");
            }
            // 启动 VPN 流量服务
            startVpn();
        } catch (Exception e) {
            Log.e("CustomVpnService", "Error in onStartCommand: " + e.getMessage(), e);
            stopSelf(); // 发生异常时停止服务
        }
        return START_STICKY;
    }

    private boolean isV2rayRunning() {
        try {
            // 执行系统命令，获取当前所有正在运行的进程
            Process process = Runtime.getRuntime().exec("ps");

            // 读取进程的输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 检查是否有包含 "v2ray" 的进程
                    if (line.contains("v2ray")) {
                        Log.i("CustomVpnService", "V2Ray process found: " + line);
                        return true;
                    }
                }
            }

            // 检查完成，没有找到 "v2ray" 相关的进程
            Log.i("CustomVpnService", "No V2Ray process is running.");
            return false;

        } catch (IOException e) {
            // 捕获异常并记录日志
            Log.e("CustomVpnService", "Error checking V2Ray process: " + e.getMessage(), e);
            return false;
        }
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
    private boolean isValidIpAddress(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleVpnTraffic(ParcelFileDescriptor vpnInterface) {
        if (vpnInterface == null || !vpnInterface.getFileDescriptor().valid()) {
            throw new IllegalArgumentException("ParcelFileDescriptor is invalid!");
        }

        byte[] packetData = new byte[32767];
        int retryCount = 0;
        try (FileInputStream inStream = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream outStream = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int length = inStream.read(packetData);
                    if (length == -1) {
                        // EOF
                        break;
                    }

                    if (length > 0) {
                        boolean handled = processPacket(packetData, length);
                        if (!handled) {
                            outStream.write(packetData, 0, length);
                        }
                    }
                    retryCount = 0; // Reset retry count after successful read
                } catch (IOException e) {
                    retryCount++;
                    Log.e("CustomVpnService", "Error reading packet. Retry attempt " + retryCount, e);
                    if (retryCount >= MAX_RETRY) { // Add constant definition
                        Log.e("CustomVpnService", "Max retry reached. Exiting loop.");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Log.e("CustomVpnService", "IO error in handleVpnTraffic", e);
        } finally {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e("CustomVpnService", "Failed to close vpnInterface", e);
            }
        }
    }
    private boolean processPacket(byte[] packetData, int length) {
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

}
