package com.alyssum.relegion.hook;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import com.alyssum.relegion.Protocol;
import io.github.libxposed.api.XposedModule;
import java.util.function.UnaryOperator;

/** Only a namespaced call from the signed module may enter the bridge. */
final class HostCalls {
    static void install(XposedModule api, ClassLoader loader, String providerName, String method,
                        UnaryOperator<Bundle> handler) throws ReflectiveOperationException {
        Class<?> provider = Reflect.findClass(providerName, loader);
        // Cover both overloads: ART may have inlined the delegating overload.
        for (int offset : new int[]{0, 1}) {
            Class<?>[] parameters = offset == 0
                    ? new Class<?>[]{String.class, String.class, Bundle.class}
                    : new Class<?>[]{String.class, String.class, String.class, Bundle.class};
            api.hook(provider.getMethod("call", parameters)).intercept(c -> {
                if (!provider.isInstance(c.getThisObject()) || !method.equals(c.getArg(offset))) return c.proceed();
                if (offset == 1 && !providerName.equals(c.getArg(0))) return c.proceed();
                ContentProvider owner = (ContentProvider)c.getThisObject();
                Context context = owner.getContext();
                // getCallingPackage verifies package/UID through the platform;
                // do not depend on the host's package-visibility query rights.
                if (!Protocol.MODULE.equals(owner.getCallingPackage())
                        || context.checkCallingPermission(Protocol.PERMISSION) != PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("Only RELEGION may use its host bridge");
                Bundle args = (Bundle)c.getArg(offset + 2);
                long identity = Binder.clearCallingIdentity();
                try { return handler.apply(args == null ? Bundle.EMPTY : args); }
                finally { Binder.restoreCallingIdentity(identity); }
            });
        }
        for (java.lang.reflect.Method caller : Class.forName("android.content.ContentProvider$Transport").getDeclaredMethods())
            if (caller.getName().equals("call")) api.deoptimize(caller);
    }
}
