package io.github.myrom.lenovopen;

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
        if (uid != Process.myUid() && (packages == null || !Arrays.asList(packages).contains(Protocol.TARGET)))
            throw new SecurityException("Only SecurityCore may publish pen state");
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
        safe.putString("error", extras.getString("error", ""));
        safe.putLong("sampled", SystemClock.elapsedRealtime());
        latest = safe;
        ConnectionNotifier.onSnapshot(getContext(), safe, extras.getString("event", ""));
        return Bundle.EMPTY;
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
