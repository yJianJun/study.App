package com.example.studyapp.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;

import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ShellUtils {

    public static void exec(String cmd) {
        try {
            Log.e("ShellUtils", String.format("exec %s", cmd));
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception e) {
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
            Log.e("hasBin", "PATH environment variable is not available");
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
                Log.e("hasBin", "Security exception occurred while checking: " + file.getAbsolutePath(), e);
            }
        }

        // 如果未找到可执行文件，返回 false
        return false;
    }

    public static String execRootCmdAndGetResult(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            Log.e("ShellUtils", "Unsafe or empty command. Aborting execution.");
            throw new IllegalArgumentException("Unsafe or empty command.");
        }

//        if (!isCommandSafe(cmd)) {  // 检查命令的合法性
//            throw new IllegalArgumentException("Detected unsafe command.");
//        }

        Process process = null;
        try {
            // 初始化并选择 shell
            if (hasBin("su")) {
                process = Runtime.getRuntime().exec("su");
            } else if (hasBin("xu")) {
                process = Runtime.getRuntime().exec("xu");
            } else if (hasBin("vu")) {
                process = Runtime.getRuntime().exec("vu");
            } else {
                process = Runtime.getRuntime().exec("sh");
            }

            // 使用 try-with-resources 关闭流
            try (OutputStream os = new BufferedOutputStream(process.getOutputStream());
                 InputStream is = process.getInputStream();
                 InputStream errorStream = process.getErrorStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream))) {

                // 异步处理错误流
                Thread errorThread = new Thread(() -> errorReader.lines().forEach(line -> Log.e("ShellUtils", "Shell Error: " + line)));
                errorThread.start();

                // 写入命令到 shell
                os.write((cmd + "\n").getBytes());
                os.write("exit\n".getBytes());
                os.flush();

                // 等待 process 执行完成
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroy();
                        throw new RuntimeException("Shell command execution timeout.");
                    }
                } else {
                    long startTime = System.currentTimeMillis();
                    while (true) {
                        try {
                            process.exitValue();
                            break;
                        } catch (IllegalThreadStateException e) {
                            if (System.currentTimeMillis() - startTime > 10000) { // 10 seconds
                                process.destroy();
                                throw new RuntimeException("Shell command execution timeout.");
                            }
                            Thread.sleep(100); // Sleep briefly before re-checking
                        }
                    }
                }

                // 读取输出流中的结果
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line).append("\n");
                }
                return output.toString().trim();
            }
        } catch (IOException | InterruptedException e) {
            Log.e("ShellUtils", "Command execution failed: " + e.getMessage());
            Thread.currentThread().interrupt(); // 恢复中断状态
            return "Error: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static void execRootCmd(String cmd) {
//        if (!isCommandSafe(cmd)) {
//            Log.e("ShellUtils", "Unsafe command, aborting.");
//            return;
//        }

        List<String> cmds = new ArrayList<>();
        cmds.add(cmd);

        try {
            // 使用同步块确保线程安全
            synchronized (ShellUtils.class) {
                List<String> results = execRootCmds(cmds);
                for (String result : results) {
                    Log.d("ShellUtils", "Command Result: " + result); // 可根据生产环境禁用日志
                }
            }
        } catch (Exception e) {
            Log.e("ShellUtils", "Error executing root command: ", e);
        }
    }

    private static boolean isCommandSafe(String cmd) {
        // 空和空字符串验证
        if (cmd == null || cmd.trim().isEmpty()) {
            return false;
        }

        // 仅允许安全字符
        if (!cmd.matches("^[a-zA-Z0-9._/:\\- ]+$")) {
            return false;
        }

        // 添加更多逻辑检查，根据预期命令规则
        // 例如：禁止多重命令，检查逻辑运算符 "&&" 或 "||"
        if (cmd.contains("&&") || cmd.contains("||")) {
            return false;
        }

        // 检查命令长度，避免过长命令用于攻击
        if (cmd.length() > 500) { // 假定命令应当限制在 500 字符以内
            return false;
        }

        return true;
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
                    Log.e("ShellUtils", "Error reading stdout", ioException);
                }
            });

            Process finalProcess = process;
            Thread errThread = new Thread(() -> {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        Log.e("ShellUtils", "Stderr: " + line);
                    }
                } catch (IOException ioException) {
                    Log.e("ShellUtils", "Error reading stderr", ioException);
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

            // 等待子进程完成
            process.waitFor();

            // 等待子线程完成
            stdThread.join();
            errThread.join();

        } catch (Exception e) {
            Log.e("ShellUtils", "Error executing commands", e);
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
                Log.e("ShellUtils", "Security exception while checking: " + bin, e);
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
