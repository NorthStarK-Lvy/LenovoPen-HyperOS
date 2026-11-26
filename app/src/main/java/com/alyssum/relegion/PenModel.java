package com.alyssum.relegion;

/** Maps a Lenovo pen's identity to the artwork family used by Lenovo's own pen app. */
public final class PenModel {
    public static final String PICASSO = "picasso";
    public static final String PARKER = "parker";
    public static final String OMAS = "omas";
    public static final String SHEAFFER = "sheaffer";
    public static final String UNKNOWN = "";
    private PenModel() {}

    /** HID product ids (vendor 0x17EF) taken from the official app's BtPenModels. */
    public static String fromProduct(int product) {
        switch (product) {
            case 0x617f: return PICASSO;
            case 0x61a1: return PARKER;
            case 0x622e: return OMAS;
            case 0x612b: return SHEAFFER;
            default: return UNKNOWN;
        }
    }

    /** Bluetooth names from the official app; the user-visible alias must not be passed here. */
    public static String fromName(String name) {
        if (name == null) return UNKNOWN;
        switch (name.trim()) {
            case "Lenovo Tab Pen Plus": return PICASSO;
            case "Lenovo Tab Pen Pro":
            case "Lenovo Tab Pen Pro 2": return PARKER;
            case "Lenovo Precision Pen":
            case "Lenovo Precision Pen 3": return SHEAFFER;
            default: return UNKNOWN;
        }
    }

    /** The HID product id is exact; the Bluetooth name covers a pen whose input device is absent. */
    public static String of(String name, int product) {
        String byProduct = fromProduct(product);
        return byProduct.isEmpty() ? fromName(name) : byProduct;
    }

    /** Features the official settings page shows for each family; Plus has no tail or body gestures. */
    public static boolean hasSlideGesture(String model) { return PARKER.equals(model); }
    public static boolean hasTailEraser(String model) { return PARKER.equals(model); }
    public static boolean hasHapticFeedback(String model) { return PARKER.equals(model); }
    public static boolean hasDualTapKeys(String model) { return PARKER.equals(model); }
}

