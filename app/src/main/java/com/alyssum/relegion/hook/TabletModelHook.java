package com.alyssum.relegion.hook;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.alyssum.relegion.tablet.ModelNamePolicy;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Only the model heading above the About tablet parameter grid is replaced. */
final class TabletModelHook {
    private final XposedModule module;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<TextView, String> originals = new WeakHashMap<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (prefs, key) -> {
        if (key == null || ModelNamePolicy.KEY.equals(key)) reload();
    };
    private volatile String name = "";
    private boolean warned;

    private TabletModelHook(XposedModule module, SharedPreferences preferences) {
        this.module = module;
        this.preferences = preferences;
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> adapter = Class.forName("com.android.settings.device.DeviceInfoAdapter", false, loader);
        Class<?> holder = Class.forName("com.android.settings.device.DeviceInfoAdapter$DeviceCardViewHolder", false, loader);
        Class<?> card = Class.forName("com.android.settings.device.DeviceCardInfo", false, loader);
        Class<?> ids = Class.forName("com.android.settings.R$id", false, loader);
        Method bind = adapter.getDeclaredMethod("onBindViewHolder", holder, int.class);
        Field type = field(adapter, "mType");
        Field cards = field(adapter, "cardInfos");
        Field view = field(holder, "card");
        Method index = card.getDeclaredMethod("getIndex");
        int titleId = ids.getField("card_big_title").getInt(null);
        if (bind.getReturnType() != void.class || type.getType() != int.class
                || cards.getType() != Array.newInstance(card, 0).getClass()
                || !View.class.isAssignableFrom(view.getType()) || index.getReturnType() != int.class || titleId == 0) {
            throw new NoSuchMethodException("Unsupported About tablet card contract");
        }
        bind.setAccessible(true);
        TabletModelHook hook = new TabletModelHook(module, module.getRemotePreferences(ModelNamePolicy.GROUP));
        hook.reload();
        module.hook(bind).intercept(chain -> {
            Object result = chain.proceed();
            try {
                Object owner = chain.getThisObject();
                Object items = cards.get(owner);
                int position = (int) chain.getArg(1);
                if (items == null || position < 0 || position >= Array.getLength(items)) return result;
                Object item = Array.get(items, position);
                if (item == null || !ModelNamePolicy.isModelTitle(type.getInt(owner), (int) index.invoke(item))) return result;
                Object root = view.get(chain.getArg(0));
                if (root instanceof View cardView && cardView.findViewById(titleId) instanceof TextView title) {
                    String stock = title.getText().toString();
                    if (Looper.myLooper() == Looper.getMainLooper()) hook.bind(title, stock);
                    else hook.main.post(() -> hook.bind(title, stock));
                }
            } catch (ReflectiveOperationException | RuntimeException error) { hook.warn(error); }
            return result;
        });
        preferencesListener(hook);
        module.deoptimize(bind);
        module.log(Log.INFO, "RELEGION", "About tablet model title hook installed (adapter=2, card=6)");
    }

    private static void preferencesListener(TabletModelHook hook) {
        hook.preferences.registerOnSharedPreferenceChangeListener(hook.changes);
    }

    private void bind(TextView title, String stock) {
        originals.put(title, stock);
        title.setText(name.isEmpty() ? stock : name);
    }

    private void reload() {
        try { name = ModelNamePolicy.normalize(preferences.getString(ModelNamePolicy.KEY, "")); }
        catch (RuntimeException error) { name = ""; warn(error); }
        main.post(() -> originals.forEach((title, stock) -> {
            if (title.isAttachedToWindow()) title.setText(name.isEmpty() ? stock : name);
        }));
    }

    private void warn(Throwable error) {
        if (!warned) { warned = true; module.log(Log.WARN, "RELEGION", "About tablet title update skipped", error); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
