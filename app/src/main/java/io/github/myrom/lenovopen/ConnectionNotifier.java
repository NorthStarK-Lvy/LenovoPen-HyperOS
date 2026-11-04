package io.github.myrom.lenovopen;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;

final class ConnectionNotifier {
    private static long lastAlert;
    private static final int ID = 0x50454e;
    private ConnectionNotifier() {}
    static synchronized void onSnapshot(Context context, Bundle state, String event) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!state.getBoolean("connected")) { manager.cancel(ID); return; }
        if (!"connected".equals(event) || !state.getBoolean("paired") || !state.getString("error", "").isEmpty()) return;
        if (!context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("connection_reminders", true)) return;
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        manager.createNotificationChannel(new NotificationChannel("pen_connection", "手写笔连接提示", NotificationManager.IMPORTANCE_HIGH));
        boolean alert = lastAlert == 0 || SystemClock.elapsedRealtime() - lastAlert >= 20000;
        if (!alert && java.util.Arrays.stream(manager.getActiveNotifications()).noneMatch(n -> n.getId() == ID)) return;
        if (alert) lastAlert = SystemClock.elapsedRealtime();
        int battery = state.getInt("battery", -1);
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, "pen_connection")
                .setSmallIcon(R.drawable.ic_pen_status)
                .setContentTitle(state.getString("name", "手写笔") + " 已连接")
                .setContentText(battery >= 0 ? "手写笔电量 " + battery + "% · 点击查看详情" : "可以开始书写 · 点击查看详情")
                .setCategory(Notification.CATEGORY_STATUS).setContentIntent(pending)
                .setAutoCancel(true).setTimeoutAfter(15000).setOnlyAlertOnce(true).build();
        manager.notify(ID, notification);
    }
}
