package com.huaming.watermark.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃现场记录：未捕获异常发生时，把完整堆栈连同机型/系统版本
 * 写入应用外部私有目录（Android/data/应用包名/files/crash_时间.txt），
 * 然后交还系统默认处理（崩溃弹窗/进程退出行为保持不变）。
 *
 * 用于真机上无法连接 adb 时的故障定位。
 */
public final class CrashGuard {

    private static final String TAG = "CrashGuard";

    private CrashGuard() {
    }

    public static void install(Context ctx) {
        final Thread.UncaughtExceptionHandler system =
                Thread.getDefaultUncaughtExceptionHandler();
        final File dir = ctx.getExternalFilesDir(null);
        if (system == null) return;

        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                if (dir != null) {
                    File f = new File(dir,
                            "crash_" + System.currentTimeMillis() + ".txt");
                    try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                        pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL
                                + " / Android " + Build.VERSION.RELEASE
                                + " (API " + Build.VERSION.SDK_INT + ")");
                        pw.println("thread: " + thread.getName());
                        pw.println();
                        pw.println(stackOf(e));
                    }
                    // 只保留最近 5 份，避免堆积
                    File[] all = dir.listFiles(
                            (d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
                    if (all != null && all.length > 5) {
                        java.util.Arrays.sort(all,
                                (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                        for (int i = 5; i < all.length; i++) all[i].delete();
                    }
                }
            } catch (Throwable ignored) {
            }
            Log.e(TAG, "uncaught", e);
            system.uncaughtException(thread, e);
        });
    }

    public static String stackOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
