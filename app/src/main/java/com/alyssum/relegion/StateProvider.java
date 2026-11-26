package com.alyssum.relegion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import java.util.Arrays;

/** One-way state sink. Does not expose device state to other applications. */
public final class StateProvider extends ContentProvider {
    private static volatile Bundle latest = new Bundle();
    public static Bundle peek() { return new Bundle(latest); }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if ("getPenConfig".equals(method)) {
            if (uid != Process.myUid() && uid != Process.SYSTEM_UID)
                throw new SecurityException("Only the module or system_server may read pen config");
            Bundle result = new Bundle();
            result.putString("clickAction", PenActionStore.action(getContext()));
            result.putString("doubleAction", PenActionStore.doubleAction(getContext()));
            result.putString("openPackage", PenActionStore.openPackage(getContext()));
            result.putStringArrayList("consumerPackages", new java.util.ArrayList<>(PenActionStore.consumers(getContext())));
            return result;
        }
        if (uid != Process.myUid() && (packages == null || !Arrays.asList(packages).contains(Protocol.TARGET)))
            throw new SecurityException("Unexpected state publisher");
        if ("getPenConfig".equals(method)) {
            if (uid != Process.myUid()) throw new SecurityException("Only the module may read pen config");
            Bundle result = new Bundle();
            result.putString("clickAction", PenActionStore.action(getContext()));
            result.putString("doubleAction", PenActionStore.doubleAction(getContext()));
            result.putString("openPackage", PenActionStore.openPackage(getContext()));
            result.putStringArrayList("consumerPackages", new java.util.ArrayList<>(PenActionStore.consumers(getContext())));
            return result;
        }
        if (!"publish".equals(method) || extras == null) throw new IllegalArgumentException("publish expected");
        Bundle safe = new Bundle();
        boolean online = extras.getBoolean("connected");
        safe.putBoolean("hook", true);
        safe.putBoolean("enabled", extras.getBoolean("enabled"));
        safe.putBoolean("paired", extras.getBoolean("paired"));
        safe.putBoolean("connected", online);
        String name = extras.getString("name", "手写笔").trim();
        safe.putString("name", name.isEmpty() ? "手写笔" : name.substring(0, Math.min(name.length(), 120)));
        safe.putInt("battery", PenIdentity.visibleBattery(online, extras.getInt("battery", -1)));
        safe.putString("device", extras.getString("device", ""));
        safe.putLong("batterySampled", extras.getLong("batterySampled"));
        safe.putString("error", extras.getString("error", ""));
        for (String key : new String[]{"serial", "version", "firmware", "hardware", "penModel", "model", "clickAction", "openPackage"}) {
            String value = extras.getString(key, "");
            safe.putString(key, value.substring(0, Math.min(value.length(), 120)));
        }
        safe.putInt("officialBattery", extras.getInt("officialBattery", -1));
        safe.putBoolean("charging", extras.getBoolean("charging"));
        safe.putBoolean("attached", extras.getBoolean("attached"));
        safe.putStringArrayList("consumerPackages", extras.getStringArrayList("consumerPackages"));
        safe.putLong("sampled", SystemClock.elapsedRealtime());
        latest = safe;
        // Deliver the accepted snapshot immediately to the visible module UI.
        // Observers receive no state themselves; peek() remains process-local.
        getContext().getContentResolver().notifyChange(Uri.parse("content://" + Protocol.AUTHORITY + "/pen"), null);
        ConnectionNotifier.onSnapshot(getContext(), safe, extras.getString("event", ""));
        return Bundle.EMPTY;
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
