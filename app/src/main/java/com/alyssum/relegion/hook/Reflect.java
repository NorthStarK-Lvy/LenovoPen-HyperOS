package com.alyssum.relegion.hook;

import java.lang.reflect.*;
import java.util.*;

/** Host-object reflection only; no calls to the legacy Xposed API. */
final class Reflect {
    private static final Map<Object, Map<String, Object>> extras = new WeakHashMap<>();
    static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(name, e); }
    }
    static Field field(Object owner, String name) {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new IllegalStateException("Missing field " + name);
    }
    static Object getObjectField(Object o, String n) {
        try { return field(o, n).get(o); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static boolean getBooleanField(Object o, String n) { return Boolean.TRUE.equals(getObjectField(o, n)); }
    static void setBooleanField(Object o, String n, boolean value) {
        try { field(o, n).setBoolean(o, value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static synchronized Object getAdditionalInstanceField(Object o, String n) {
        Map<String, Object> v = extras.get(o); return v == null ? null : v.get(n);
    }
    static synchronized void setAdditionalInstanceField(Object o, String n, Object v) {
        extras.computeIfAbsent(o, key -> new HashMap<>()).put(n, v);
    }
    static synchronized Object removeAdditionalInstanceField(Object o, String n) {
        Map<String, Object> v = extras.get(o); if (v == null) return null;
        Object old = v.remove(n); if (v.isEmpty()) extras.remove(o); return old;
    }
    static Object callMethod(Object o, String name, Object... args) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                boolean match = true;
                Class<?>[] types = m.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    if (args[i] == null ? types[i].isPrimitive() : !boxed(types[i]).isInstance(args[i])) { match = false; break; }
                }
                if (!match) continue;
                try { m.setAccessible(true); return m.invoke(o, args); }
                catch (InvocationTargetException e) { throw new IllegalStateException(e.getCause()); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
        }
        throw new IllegalStateException("Missing method " + name);
    }
    private static Class<?> boxed(Class<?> c) {
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return c;
    }
}
