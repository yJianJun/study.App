package com.example.studyapp.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;

import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ShellUtils {

  public static void exec(String cmd) {
    try {
      LogFileUtil.logAndWrite(Log.INFO, "ShellUtils", "Executing command: " + cmd, null);
      Process process = Runtime.getRuntime().exec(cmd);
      process.waitFor();
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error executing command: " + e.getMessage(), e);
    }
  }

  public static int getPid(Process p) {
    int pid = -1;
    try {
      Field f = p.getClass().getDeclaredField("pid");
      f.setAccessible(true);
      pid = f.getInt(p);
      f.setAccessible(false);
    } catch (Throwable e) {
      pid = -1;
    }
    return pid;
  }

  public static boolean hasBin(String binName) {
    // 验证 binName 是否符合规则
    if (binName == null || binName.isEmpty()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Invalid bin name",null);
      throw new IllegalArgumentException("Bin name cannot be null or empty");
    }

    for (char c : binName.toCharArray()) {
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '-') {
        throw new IllegalArgumentException("Invalid bin name");
      }
    }

    // 获取 PATH 环境变量
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "PATH environment variable is not available", null);
      return false;
    }

    // 使用适合当前系统的路径分隔符分割路径
    String[] paths = pathEnv.split(File.pathSeparator);
    for (String path : paths) {
      File file = new File(path, binName); // 使用 File 构造完整路径
      try {
        // 检查文件是否可执行
        if (file.canExecute()) {
          return true;
        }
      } catch (SecurityException e) {
        LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Security exception occurred while checking: " + file.getAbsolutePath(), e);
      }
    }

    // 如果未找到可执行文件，返回 false
    return false;
  }

  public static String execRootCmdAndGetResult(String cmd) {
    Log.d("ShellUtils", "execRootCmdAndGetResult - Started execution for command: " + cmd);
    if (cmd == null || cmd.trim().isEmpty()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Unsafe or empty command. Aborting execution.", null);
      throw new IllegalArgumentException("Unsafe or empty command.");
    }
    // if (!isCommandSafe(cmd)) {  // 检查命令的合法性
    //   Log.e("ShellUtils", "Detected unsafe command. Aborting execution.");
    //   throw new IllegalArgumentException("Detected unsafe command.");
    // }

    Process process = null;
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Log.d("ShellUtils", "Determining appropriate shell for execution...");
      if (hasBin("su")) {
        Log.d("ShellUtils", "'su' binary found, using 'su' shell.");
        process = Runtime.getRuntime().exec("su");
      } else if (hasBin("xu")) {
        Log.d("ShellUtils", "'xu' binary found, using 'xu' shell.");
        process = Runtime.getRuntime().exec("xu");
      } else if (hasBin("vu")) {
        Log.d("ShellUtils", "'vu' binary found, using 'vu' shell.");
        process = Runtime.getRuntime().exec("vu");
      } else {
        Log.d("ShellUtils", "No specific binary found, using 'sh' shell.");
        process = Runtime.getRuntime().exec("sh");
      }

      try (OutputStream os = new BufferedOutputStream(process.getOutputStream());
          InputStream is = process.getInputStream();
          InputStream errorStream = process.getErrorStream();
          BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
          BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {

        Log.d("ShellUtils", "Starting separate thread to process error stream...");
        executor.submit(() -> {
          String line;
          try {
            while ((line = errorReader.readLine()) != null) {
              LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Shell Error: " + line, null);
            }
          } catch (IOException e) {
            LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error while reading process error stream: " + e.getMessage(), e);
          }
        });

        Log.d("ShellUtils", "Writing the command to the shell...");
        os.write((cmd + "\n").getBytes());
        os.write("exit\n".getBytes());
        os.flush();
        Log.d("ShellUtils", "Command written to shell. Waiting for process to complete.");

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
          Log.d("ShellUtils", "Shell Output: " + line);
          output.append(line).append("\n");
        }

        Log.d("ShellUtils", "Awaiting process termination...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (!process.waitFor(10, TimeUnit.SECONDS)) {
            LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Process execution timed out. Destroying process.", null);
            process.destroyForcibly();
            throw new RuntimeException("Shell command execution timeout.");
          }
        } else {
          Log.d("ShellUtils", "Using manual time tracking method for process termination (API < 26).");
          long startTime = System.currentTimeMillis();
          while (true) {
            try {
              process.exitValue();
              break;
            } catch (IllegalThreadStateException e) {
              if (System.currentTimeMillis() - startTime > 10000) { // 10 seconds
                LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Process execution timed out (manual tracking). Destroying process.", null);
                process.destroy();
                throw new RuntimeException("Shell command execution timeout.");
              }
              Thread.sleep(100); // Sleep briefly before re-checking
            }
          }
        }

        Log.d("ShellUtils", "Process terminated successfully. Returning result.");
        return output.toString().trim();
      }
    } catch (IOException | InterruptedException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Command execution failed: " + e.getMessage(), e);
      Thread.currentThread().interrupt();
      return "Error: " + e.getMessage();
    } finally {
      if (process != null) {
        Log.d("ShellUtils", "Finalizing process. Attempting to destroy it.");
        process.destroy();
      }
      executor.shutdown();
      Log.d("ShellUtils", "Executor service shut down.");
    }
  }

  public static void execRootCmd(String cmd) {
    // 校验命令是否安全
    if (!isCommandSafe(cmd)) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Unsafe command, aborting.", null);
      return;
    }
    List<String> cmds = new ArrayList<>();
    cmds.add(cmd);

    // 使用同步锁保护线程安全
    synchronized (ShellUtils.class) {
      try {
        List<String> results = execRootCmds(cmds);
        // 判断是否需要打印输出，仅用于开发调试阶段
        for (String result : results) {
          Log.d("ShellUtils", "Command Result: " + result);
        }
      } catch (Exception e) {
        LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Unexpected error: " + e.getMessage(), e);
      }
    }
  }


  private static boolean isCommandSafe(String cmd) {
    // 检查空值和空字符串
    if (cmd == null || cmd.trim().isEmpty()) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Rejected command: empty or null value.", null);
      return false;
    }

    // 检查非法字符
    if (!cmd.matches("^[a-zA-Z0-9._/:\\-~`'\" *|]+$")) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Rejected command due to illegal characters: " + cmd, null);
      return false;
    }

    // 检查多命令（逻辑运算符限制）
    if (cmd.contains("&&") || cmd.contains("||")) {
      Log.d("ShellUtils", "Command contains logical operators.");
      if (!isExpectedMultiCommand(cmd)) {
        LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Rejected command due to prohibited structure: " + cmd, null);
        return false;
      }
    }

    // 路径遍历保护
    if (cmd.contains("../") || cmd.contains("..\\")) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Rejected command due to path traversal attempt: " + cmd, null);
      return false;
    }

    // 命令长度限制
    if (cmd.startsWith("tar") && cmd.length() > 800) { // 特定命令支持更长长度
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Command rejected due to excessive length.", null);
      return false;
    } else if (cmd.length() > 500) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Command rejected due to excessive length.", null);
      return false;
    }

    Log.d("ShellUtils", "Command passed safety checks: " + cmd);
    return true;
  }

  // 附加方法：检查多命令是否符合预期
  private static boolean isExpectedMultiCommand(String cmd) {
    // 判断是否为允许的命令组合，比如 `cd` 或 `tar` 组合命令
    return cmd.matches("^cd .+ && (tar|zip|cp).+");
  }

  public static List<String> execRootCmds(List<String> cmds) {
    List<String> results = new ArrayList<>();
    Process process = null;
    try {
      // 初始化 Shell 环境
      process = hasBin("su") ? Runtime.getRuntime().exec("su") : Runtime.getRuntime().exec("sh");

      // 启动读取线程
      Process stdProcess = process;
      Thread stdThread = new Thread(() -> {
        try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(stdProcess.getInputStream()))) {
          List<String> localResults = new ArrayList<>();
          String line;
          while ((line = stdReader.readLine()) != null) {
            localResults.add(line);
            Log.d("ShellUtils", "Stdout: " + line);
          }
          synchronized (results) {
            results.addAll(localResults);
          }
        } catch (IOException ioException) {
          LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error reading stdout", ioException);
        }
      });

      Process finalProcess = process;
      Thread errThread = new Thread(() -> {
        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
          String line;
          while ((line = errReader.readLine()) != null) {
            LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Stderr: " + line, null);
          }
        } catch (IOException ioException) {
          LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error reading stderr", ioException);
        }
      });

      // 启动子线程
      stdThread.start();
      errThread.start();

      try (OutputStream os = process.getOutputStream()) {
        for (String cmd : cmds) {
//                    if (!isCommandSafe(cmd)) {
//                        Log.w("ShellUtils", "Skipping unsafe command: " + cmd);
//                        continue;
//                    }
          os.write((cmd + "\n").getBytes());
          Log.d("ShellUtils", "Executing command: " + cmd);
        }
        os.write("exit\n".getBytes());
        os.flush();
      }

      try {
        // 执行命令、等待解决
        process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // 恢复中断
        LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error executing commands", e);
      }

      // 等待子线程完成
      stdThread.join();
      errThread.join();

    } catch (InterruptedIOException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error reading stdout: Interrupted", e);
      Thread.currentThread().interrupt(); // 恢复线程的中断状态
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Error executing commands", e);
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
    return results;
  }

  public static boolean hasRootAccess() {
    // 记录是否出现安全异常
    boolean hasSecurityError = false;

    // 检查二进制文件
    String[] binariesToCheck = {"su", "xu", "vu"};
    for (String bin : binariesToCheck) {
      try {
        if (hasBin(bin)) {
          return true;
        }
      } catch (SecurityException e) {
        hasSecurityError = true;
        LogFileUtil.logAndWrite(Log.ERROR, "ShellUtils", "Security exception while checking: " + bin, e);
      }
    }

    // 判断如果发生安全异常则反馈问题
    if (hasSecurityError) {
      Log.w("ShellUtils", "Potential security error detected while checking root access.");
    }

    // 没有找到合法的二进制文件，则认为无root权限
    return false;
  }
}
