package com.alyssum.relegion;

/** Shared scene data used by the viewport shader and the native wordmark gradient. */
public final class AboutColorField {
    public final float[] centers = new float[16];
    public final float[] colors = new float[12];
    private final float[] scratch = new float[3];
    private float width = 1, height = 1, aspect = 1, time;
    private float red, green, blue;
    private boolean dark;

    public void configure(float width, float height, float seconds, boolean dark, int background) {
        this.width = Math.max(width, 1); this.height = Math.max(height, 1);
        this.aspect = clamp(this.width / this.height, .55f, 2.8f);
        this.time = seconds * .32f; this.dark = dark;
        red = ((background >> 16) & 255) / 255f;
        green = ((background >> 8) & 255) / 255f;
        blue = (background & 255) / 255f;
        center(0, -.30f + .24f * cos(time), .22f + .20f * sin(time * .75f), .58f, .55f);
        center(1, .32f + .22f * sin(time * .8f), .36f + .21f * cos(time), .65f, .62f);
        center(2, -.12f + .34f * sin(time * .65f), .78f + .20f * sin(time + 1.2f), .73f, .56f);
        center(3, .10f + .28f * cos(time * .7f), 1f + .18f * cos(time), .90f, .72f);
        float phase = .5f + .5f * sin(seconds * .24f);
        float vr = dark ? .20f : .66f, vg = dark ? .12f : .67f, vb = dark ? .48f : .99f;
        float rr = dark ? .42f : 1f, rg = dark ? .10f : .68f, rb = dark ? .35f : .81f;
        float br = dark ? .06f : .56f, bg = dark ? .24f : .78f, bb = dark ? .53f : 1f;
        float mr = dark ? .03f : .61f, mg = dark ? .33f : .91f, mb = dark ? .32f : .85f;
        palette(0, vr, vg, vb, br, bg, bb, phase);
        palette(1, rr, rg, rb, vr, vg, vb, phase);
        palette(2, br, bg, bb, rr, rg, rb, phase);
        palette(3, mr, mg, mb, br, bg, bb, phase);
    }

    private void center(int i, float x, float y, float rx, float ry) {
        int at = i * 4; centers[at] = x; centers[at + 1] = y; centers[at + 2] = rx; centers[at + 3] = ry;
    }
    private void palette(int i, float r, float g, float b, float r2, float g2, float b2, float t) {
        int at = i * 3; colors[at] = mix(r, r2, t); colors[at + 1] = mix(g, g2, t); colors[at + 2] = mix(b, b2, t);
    }
    private void sample(float x, float y) {
        float px = (x / width - .5f) * aspect, py = y / height;
        float dx = .11f * sin(py * 5.2f + time), dy = .11f * cos(px * 4.8f - time * .8f);
        px += dx; py += dy;
        float sum = 0, r = 0, g = 0, b = 0;
        for (int i = 0; i < 4; i++) {
            int at = i * 4; float u = (px - centers[at]) / centers[at + 2], v = (py - centers[at + 1]) / centers[at + 3];
            float weight = (float) Math.exp(-(u * u + v * v) * 1.6f);
            sum += weight; r += colors[i * 3] * weight; g += colors[i * 3 + 1] * weight; b += colors[i * 3 + 2] * weight;
        }
        float coverage = clamp(.54f + sum * .19f, .54f, .9f), scale = 1f / Math.max(sum, .001f);
        scratch[0] = mix(red, r * scale, coverage);
        scratch[1] = mix(green, g * scale, coverage);
        scratch[2] = mix(blue, b * scale, coverage);
    }

    public int inkAt(float x, float y, float radius) {
        sample(x, y); float r = scratch[0] * .36f, g = scratch[1] * .36f, b = scratch[2] * .36f;
        for (int i = 0; i < 4; i++) {
            sample(x + (i == 0 ? radius : i == 1 ? -radius : 0), y + (i == 2 ? radius : i == 3 ? -radius : 0));
            r += scratch[0] * .16f; g += scratch[1] * .16f; b += scratch[2] * .16f;
        }
        float luma = .2126f * r + .7152f * g + .0722f * b;
        float tone = dark ? .84f : .12f, strength = dark ? 1.8f : 2.6f;
        int ri = Math.round(clamp(tone + (r - luma) * strength, dark ? .55f : .025f, dark ? 1f : .44f) * 255);
        int gi = Math.round(clamp(tone + (g - luma) * strength, dark ? .55f : .025f, dark ? 1f : .44f) * 255);
        int bi = Math.round(clamp(tone + (b - luma) * strength, dark ? .55f : .025f, dark ? 1f : .44f) * 255);
        return 0xff000000 | ri << 16 | gi << 8 | bi;
    }
    private static float clamp(float n, float min, float max) { return Math.max(min, Math.min(n, max)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float sin(float n) { return (float) Math.sin(n); }
    private static float cos(float n) { return (float) Math.cos(n); }
}
