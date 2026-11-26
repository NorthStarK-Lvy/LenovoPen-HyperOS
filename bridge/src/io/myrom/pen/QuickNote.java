package io.myrom.pen;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import java.lang.reflect.Method;
import java.util.List;
import java.nio.charset.StandardCharsets;

public final class QuickNote {
    private static final String PKG = "com.miui.creation";
    private static final String ACTION = "com.miui.creation.action.INSERT_CREATION";
    private static final String MIME = "vnd.android.cursor.item/create_creation";
    private static final String FLAGS = "native_intent_server_calculated_freeform";
    private static Context systemContext() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        // app_process skips the zygote's Android 17 process-local shared-memory setup.
        // This allocation belongs to this short-lived launcher, not system_server.
        Class<?> shared = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        Object instance = shared.getMethod("create").invoke(null);
        shared.getMethod("setInstance", shared).invoke(null, instance);
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        return (Context) type.getMethod("getSystemContext").invoke(thread);
    }
    @SuppressWarnings("deprecation")
    private static String topPackage(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            ComponentName top = tasks.isEmpty() ? null : tasks.get(0).topActivity;
            return top == null ? "" : top.getPackageName();
        } catch (RuntimeException e) { return ""; }
    }
    public static void main(String[] args) {
        try {
            if (args.length != 1 || !(args[0].equals("--probe") || args[0].equals("--trigger") || args[0].equals("--screen")))
                throw new IllegalArgumentException("--probe, --trigger or --screen");
            Context context = systemContext();
            int user = (Integer) ActivityManager.class.getDeclaredMethod("getCurrentUser").invoke(null);
            UserHandle handle = (UserHandle) UserHandle.class.getDeclaredMethod("of", int.class).invoke(null, user);
            Context target = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class).invoke(context, handle, 0);
            PowerManager pm = (PowerManager) target.getSystemService(Context.POWER_SERVICE);
            KeyguardManager km = (KeyguardManager) target.getSystemService(Context.KEYGUARD_SERVICE);
            if (args[0].equals("--screen")) {
                if (!pm.isInteractive() || km.isKeyguardLocked()) {
                    System.out.println("SCREEN_RECOGNITION_SKIPPED screen_off_or_locked=1");
                    System.exit(0);
                }
                // Same explicit service and extras as this ROM's
                // ShortCutActionsUtils.launchXiaoaiScreenIdentification.
                Process request = new ProcessBuilder("/system/bin/am", "start-foreground-service",
                    "--user", Integer.toString(user), "-a", "android.intent.action.ASSIST",
                    "-n", "com.miui.voiceassist/com.xiaomi.voiceassistant.VoiceService",
                    "--es", "voice_assist_start_from_key", "stylus_double_click",
                    "--es", "voice_assist_function_key", "start_screen_recognition")
                    .redirectErrorStream(true).start();
                String output = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int result = request.waitFor();
                System.out.print(output);
                if (result != 0 || output.contains("Error:") || output.contains("Exception"))
                    throw new IllegalStateException("Screen recognition service rejected result=" + result);
                System.out.println("SCREEN_RECOGNITION_REQUEST_ACCEPTED");
                System.exit(0);
            }
            String top = topPackage(target);
            String scene = !pm.isInteractive() ? "off_screen" : km.isKeyguardLocked() ? "keyguard" : "com.miui.home".equals(top) ? "home" : "app";
            Intent intent = new Intent(ACTION).setPackage(PKG).setType(MIME)
                .addFlags(0x14000000).putExtra("shortcut", "stylus").putExtra("scene", scene);
            boolean locked = scene.equals("off_screen") || scene.equals("keyguard");
            if (locked) intent.putExtra("StartActivityWhenLocked", true);
            ResolveInfo resolved = target.getPackageManager().resolveActivity(intent, 0);
            if (resolved == null || resolved.activityInfo == null || !PKG.equals(resolved.activityInfo.packageName))
                throw new IllegalStateException("Native creation intent did not resolve");
            Bundle options = null;
            if (scene.equals("app")) {
                ActivityOptions value = ActivityOptions.makeBasic();
                ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(value, 5);
                value.setLaunchDisplayId(0);
                // Do not calculate a MIUI scale in an unregistered process. -1 is
                // the platform sentinel requesting server-side scale calculation.
                Object injector = ActivityOptions.class.getMethod("getActivityOptionsInjector").invoke(value);
                float scale = (Float) injector.getClass().getMethod("getFreeformScale").invoke(injector);
                if (scale != -1.0f || value.getLaunchBounds() != null)
                    throw new IllegalStateException("Unexpected explicit freeform geometry");
                System.out.println("WINDOW_GEOMETRY=server_default scale_sentinel=" + scale);
                options = value.toBundle();
            }
            System.out.println("RESOLVED=" + resolved.activityInfo.name + " USER=" + user + " SCENE=" + scene + " TOP=" + top + " OPTIONS=" + (options != null) + " ROUTE=" + FLAGS);
            if (args[0].equals("--probe")) { System.out.println("PROBE_OK activities_started=0"); System.exit(0); }
            if (PKG.equals(top) || "com.miui.notes".equals(top)) {
                System.out.println("ALREADY_FOREGROUND no_new_note=1"); System.exit(0);
            }
            // A command-line process is not an AMS-registered application. Pass no
            // application-thread token, as the system's activity shell command does.
            Class<?> taskType = Class.forName("android.app.IActivityTaskManager");
            Object tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method start = taskType.getMethod("startActivityAsUser", Class.forName("android.app.IApplicationThread"),
                String.class, String.class, Intent.class, String.class, IBinder.class, String.class,
                int.class, int.class, Class.forName("android.app.ProfilerInfo"), Bundle.class, int.class);
            int result = (Integer) start.invoke(tasks, null, "com.android.shell", null, intent, MIME,
                null, null, 0, 0, null, options, user);
            if (result < 0) throw new IllegalStateException("Activity start rejected result=" + result);
            System.out.println("QUICK_NOTE_START_ACCEPTED result=" + result);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(); System.exit(1);
        }
    }
}
