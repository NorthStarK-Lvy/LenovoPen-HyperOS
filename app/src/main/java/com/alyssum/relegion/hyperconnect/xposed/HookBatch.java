package com.alyssum.relegion.hyperconnect.xposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;

/** Rolls back a partially registered feature if the ROM contract cannot be installed. */
final class HookBatch implements AutoCloseable {
    private final XposedModule module;
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
    private boolean committed;

    HookBatch(XposedModule module) { this.module = module; }

    XposedInterface.HookBuilder hook(Executable executable) {
        XposedInterface.HookBuilder delegate = module.hook(executable);
        return new XposedInterface.HookBuilder() {
            @Override public XposedInterface.HookBuilder setPriority(int priority) {
                delegate.setPriority(priority); return this;
            }
            @Override public XposedInterface.HookBuilder setExceptionMode(XposedInterface.ExceptionMode mode) {
                delegate.setExceptionMode(mode); return this;
            }
            @Override public XposedInterface.HookBuilder setId(String id) {
                delegate.setId(id); return this;
            }
            @Override public XposedInterface.HookHandle intercept(XposedInterface.Hooker hooker) {
                XposedInterface.HookHandle handle = delegate.intercept(hooker);
                handles.add(handle); return handle;
            }
        };
    }

    void commit() { committed = true; }

    @Override public void close() {
        if (committed) return;
        for (int i = handles.size() - 1; i >= 0; i--) {
            try { handles.get(i).unhook(); } catch (RuntimeException ignored) { }
        }
        handles.clear();
    }
}
