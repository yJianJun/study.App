package com.example.studyapp.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @Time: 2025/6/20 12:01
 * @Creator: 初屿贤
 * @File: MockTools
 * @Project: study.App
 * @Description:
 */
public class MockTools {
  public static String exec(String cmd) {
    String retString = "";

    // 创建 socket
    String myCmd = "SU|" + cmd;
    try (Socket mSocket = new Socket()) {
      InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", 12345);

      // 设置连接超时时间，单位毫秒
      mSocket.connect(inetSocketAddress, 5000);

      try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8"));
          BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "UTF-8"))) {

        bufferedWriter.write(myCmd + "\r\n");
        bufferedWriter.flush();

        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          stringBuilder.append(line).append("\n");
        }
        retString = stringBuilder.toString();
      }
    } catch (IOException e) {
      // 记录 IO 异常，具体到日志或执行特定的恢复操作
      e.printStackTrace();
    } catch (Exception ex) {
      // 捕获其他异常并合理处理
      ex.printStackTrace();
    }
    return retString;
  }


}
