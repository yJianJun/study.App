package com.example.studyapp.utils;

import java.io.BufferedReader;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
        if (!binName.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid bin name");
        }

        String[] paths = System.getenv("PATH").split(":");
        for (String path : paths) {
            File file = new File(path + File.separator + binName);
            try {
                if (file.exists() && file.canExecute()) {
                    return true;
                }
            } catch (SecurityException e) {
                Log.e("hasBin", "Security exception occurred: " + e.getMessage());
            }
        }
        return false;
    }

    public static String execRootCmdAndGetResult(String cmd){
        if (cmd == null || cmd.trim().isEmpty()) {
            Log.e("ShellUtils", "Unsafe or empty command. Aborting execution.");
            throw new IllegalArgumentException("Unsafe or empty command.");
        }

        Process process = null;
        OutputStream os = null;
        BufferedReader br = null;
        try {
            if (hasBin("su")) {
                Log.e("ShellUtils", "Attempting to execute command: " + cmd);
                process = Runtime.getRuntime().exec("su");
            } else if (hasBin("xu")) {
                process = Runtime.getRuntime().exec("xu");
            } else if (hasBin("vu")) {
                process = Runtime.getRuntime().exec("vu");
            } else {
                process = Runtime.getRuntime().exec("sh");
            }

            os = process.getOutputStream();
            os.write((cmd + "\n").getBytes());
            os.write(("exit\n").getBytes());
            os.flush();

            // Handle error stream on a separate thread
            InputStream errorStream = process.getErrorStream();
            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        Log.e("ShellUtils", "Error: " + errorLine);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            process.waitFor();
            br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    public static void execRootCmd(String cmd) {
        if (!isCommandSafe(cmd)) {
            Log.e("ShellUtils", "Unsafe command, aborting.");
            return;
        }
        List<String> cmds = new ArrayList<>();
        cmds.add(cmd);
        execRootCmds(cmds);
    }

    private static boolean isCommandSafe(String cmd) {
        return cmd.matches("^[a-zA-Z0-9._/:\\- ]+$");
    }

    public static List<String> execRootCmds(List<String> cmds) {
        List<String> results = new ArrayList<>();
        Process process = null;
        try {
            // 初始化 Shell 环境
            process = hasBin("su") ? Runtime.getRuntime().exec("su") : Runtime.getRuntime().exec("sh");
            try (OutputStream os = process.getOutputStream();
                 InputStream is = process.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                for (String cmd : cmds) {
                    Log.d("ShellUtils", "Executing command: " + cmd);
                    os.write((cmd + "\n").getBytes());
                }
                os.write("exit\n".getBytes());
                os.flush();
                process.waitFor();
                // 获取命令输出结果
                String line;
                while ((line = reader.readLine()) != null) {
                    results.add(line);
                    Log.d("ShellUtils", "Command output: " + line);
                }
            }
        } catch (Exception e) {
            Log.e("ShellUtils", "Error executing commands: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return results;
    }
}
