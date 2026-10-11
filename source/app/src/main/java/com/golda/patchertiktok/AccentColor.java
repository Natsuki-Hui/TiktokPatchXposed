package com.golda.patchertiktok;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;

import java.lang.reflect.Method;

/**
 * Replaces TikTok's brand red with the user's color, and for the AMOLED theme the dark greys of
 * TikTok's dark theme with black. TikTok's color resources have obfuscated names, so colors are
 * matched by value as they are read (resources, theme attributes, color lists and parsed hex
 * strings); the alpha of each use is kept. Installed only when a switch is on at startup, so the
 * hooks cost nothing otherwise.
 */
final class AccentColor {
    /** TikTok's primary red, without alpha. */
    static final int BRAND = 0xFE2C55;
    /** The red and its lighter dark-theme shades (icons; a liked comment's count), all replaced. */
    private static final int[] BRANDS = {BRAND, 0xFF3B5C, 0xFF576F};
    /** TikTok's icons drawn from pictures: the follow "+" under the avatar is a red and white bitmap. */
    private static final String TUX_ICON = "com.bytedance.tux.drawable.TuxIconDrawable";

    /** Background greys of TikTok's dark theme (pages, sheets, bars). */
    private static final int[] DARK = {0x121212, 0x161616, 0x161823, 0x181818, 0x1E1E1E};

    private static volatile int accent;
    private static volatile boolean recolor;
    private static volatile boolean amoled;
    private static int hooks;

    private AccentColor() { }

    static void install() {
        accent = (int) Prefs.number(Prefs.ACCENT_COLOR) & 0xFFFFFF;
        recolor = Prefs.on(Prefs.ACCENT) && accent != BRAND;
        amoled = Prefs.on(Prefs.AMOLED);
        if (!recolor && !amoled) return;
        hookColors(Resources.class);
        hookColors(TypedArray.class);
        hooks += Hooks.hookAll(Color.class, "parseColor", COLOR);
        // Buttons such as Follow paint their shape and text with colors set in code, and vector
        // icons read theirs as a "complex color" while inflating.
        hooks += Hooks.hookAll(TypedArray.class, "getComplexColor", COMPLEX);
        // TikTok's design system reads its color tokens straight from the theme into a TypedValue.
        hooks += Hooks.hookAll(Resources.Theme.class, "resolveAttribute", THEME);
        hooks += Hooks.hookAll(android.graphics.drawable.GradientDrawable.class, "setColor", ARGUMENT);
        hooks += Hooks.hookAll(android.graphics.drawable.GradientDrawable.class, "setStroke", ARGUMENT);
        hooks += Hooks.hookAll(android.view.View.class, "setBackgroundColor", ARGUMENT);
        hooks += Hooks.hookAll(android.widget.TextView.class, "setTextColor", ARGUMENT);
        RuntimeLog.log("colors: hooks=" + hooks + " accent=" + recolor + " amoled=" + amoled);
    }

    /**
     * Icons painted from bitmaps keep their red pixels whatever the color hooks return, so those
     * with TikTok's red get a color matrix that turns the red into the accent and keeps white,
     * grey and black as they are.
     */
    static void installIcons(ClassLoader loader) {
        if (!recolor) return;
        Class<?> icon = Hooks.findClass(TUX_ICON, loader);
        if (icon == null) {
            RuntimeLog.log("accent color: picture icons not found");
            return;
        }
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        for (java.lang.reflect.Field field : icon.getDeclaredFields()) {
            if (field.getType() == android.graphics.drawable.Drawable.class) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        Hooks.hookConstructors(icon, new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                for (java.lang.reflect.Field field : fields) {
                    try {
                        Object inner = field.get(param.thisObject);
                        if (inner instanceof android.graphics.drawable.BitmapDrawable) recolor((android.graphics.drawable.BitmapDrawable) inner);
                    } catch (IllegalAccessException ignored) {
                        // Left in TikTok's colors.
                    }
                }
            }
        });
    }

    /** Icon bitmaps are shared between drawables, so each one is checked once. */
    private static final java.util.Map<android.graphics.Bitmap, Boolean> CHECKED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile android.graphics.ColorMatrixColorFilter brandFilter;

    private static void recolor(android.graphics.drawable.BitmapDrawable drawable) {
        android.graphics.Bitmap bitmap = drawable.getBitmap();
        if (bitmap == null || bitmap.getConfig() == android.graphics.Bitmap.Config.HARDWARE) return;
        Boolean brand = CHECKED.get(bitmap);
        if (brand == null) {
            brand = hasBrand(bitmap);
            CHECKED.put(bitmap, brand);
        }
        if (!brand) return;
        if (brandFilter == null) brandFilter = new android.graphics.ColorMatrixColorFilter(brandMatrix());
        drawable.setColorFilter(brandFilter);
    }

    private static boolean hasBrand(android.graphics.Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(1, Math.max(width, height) / 48);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int pixel = bitmap.getPixel(x, y);
                if ((pixel >>> 24) == 0xFF && isBrand(pixel & 0xFFFFFF)) return true;
            }
        }
        return false;
    }

    /**
     * Each output channel is a·R + b·(G + B)/2 with a + b = 1, so greys map to themselves, and a is
     * chosen so the brand red lands on the accent.
     */
    private static float[] brandMatrix() {
        float red = (BRAND >> 16) & 0xFF;
        float rest = (((BRAND >> 8) & 0xFF) + (BRAND & 0xFF)) / 2f;
        float[] matrix = new float[20];
        int[] target = {(accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF};
        for (int channel = 0; channel < 3; channel++) {
            float a = (target[channel] - rest) / (red - rest);
            float b = (1 - a) / 2f;
            matrix[channel * 5] = a;
            matrix[channel * 5 + 1] = b;
            matrix[channel * 5 + 2] = b;
        }
        matrix[18] = 1f;
        return matrix;
    }

    /** TikTok serves resources through its own Resources subclass, which overrides the getters. */
    static void installFor(Context context) {
        if ((!recolor && !amoled) || context == null) return;
        Class<?> type = context.getResources().getClass();
        while (type != null && type != Resources.class) {
            hookColors(type);
            type = type.getSuperclass();
        }
    }

    private static void hookColors(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
            if (method.getName().equals("getColor") && method.getReturnType() == int.class) {
                Hooks.hook(method, COLOR);
                hooks++;
            } else if (method.getName().equals("getColorStateList") && method.getReturnType() == ColorStateList.class) {
                Hooks.hook(method, LIST);
                hooks++;
            }
        }
    }

    private static final Hooks.Hook COLOR = new Hooks.Hook() {
        @Override protected void after(Hooks.Call param) {
            Object value = param.getResult();
            if (value instanceof Integer) param.setResult(swap((Integer) value));
        }
    };

    private static final Hooks.Hook LIST = new Hooks.Hook() {
        @Override protected void after(Hooks.Call param) {
            Object value = param.getResult();
            if (!(value instanceof ColorStateList)) return;
            ColorStateList list = (ColorStateList) value;
            ColorStateList swapped = swap(list);
            if (swapped != list) param.setResult(swapped);
        }
    };

    /** Swaps the brand red in a color argument (an int or a color list) before the call uses it. */
    private static final Hooks.Hook ARGUMENT = new Hooks.Hook() {
        @Override protected void before(Hooks.Call param) {
            Object[] args = param.args;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Integer && param.method instanceof Method
                        && ((Method) param.method).getParameterTypes()[i] == int.class) {
                    int color = (Integer) args[i];
                    // setStroke(width, color): only the color argument looks like an opaque red.
                    if (swap(color) != color) args[i] = swap(color);
                } else if (args[i] instanceof ColorStateList) {
                    args[i] = swap((ColorStateList) args[i]);
                }
            }
        }
    };

    private static final Hooks.Hook THEME = new Hooks.Hook() {
        @Override protected void after(Hooks.Call param) {
            if (!Boolean.TRUE.equals(param.getResult()) || !(param.args[1] instanceof android.util.TypedValue)) return;
            android.util.TypedValue value = (android.util.TypedValue) param.args[1];
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                value.data = swap(value.data);
            }
        }
    };

    private static final Hooks.Hook COMPLEX = new Hooks.Hook() {
        @Override protected void after(Hooks.Call param) {
            Object value = param.getResult();
            if (!(value instanceof ColorStateList)) return;
            ColorStateList list = (ColorStateList) value;
            ColorStateList swapped = swap(list);
            if (swapped != list) param.setResult(swapped);
        }
    };

    /**
     * Swaps the red in every state of a color list, not only the default one: a liked comment's
     * count, for example, is red only in its selected state. The states are read back through the
     * list's public parcel form.
     */
    static ColorStateList swap(ColorStateList list) {
        if (!list.isStateful()) {
            int color = list.getDefaultColor();
            return swap(color) == color ? list : ColorStateList.valueOf(swap(color));
        }
        ColorStateList known = SWAPPED.get(list);
        if (known != null) return known == UNCHANGED ? list : known;
        ColorStateList swapped = rebuild(list);
        // A value pointing back at its key would keep it alive forever.
        SWAPPED.put(list, swapped == list ? UNCHANGED : swapped);
        return swapped;
    }

    private static final ColorStateList UNCHANGED = ColorStateList.valueOf(0);

    /** Lists are shared by many views (often from the resource cache), so each is rebuilt once. */
    private static final java.util.Map<ColorStateList, ColorStateList> SWAPPED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static ColorStateList rebuild(ColorStateList list) {
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            list.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            int count = parcel.readInt();
            int[][] states = new int[count][];
            for (int i = 0; i < count; i++) states[i] = parcel.createIntArray();
            int[] colors = parcel.createIntArray();
            if (colors == null || colors.length != count) return list;
            boolean changed = false;
            for (int i = 0; i < count; i++) {
                int swapped = swap(colors[i]);
                changed |= swapped != colors[i];
                colors[i] = swapped;
            }
            return changed ? new ColorStateList(states, colors) : list;
        } catch (RuntimeException unreadable) {
            return list;
        } finally {
            parcel.recycle();
        }
    }

    private static boolean isBrand(int rgb) {
        for (int brand : BRANDS) if (rgb == brand) return true;
        return false;
    }

    static int swap(int color) {
        int rgb = color & 0xFFFFFF;
        if (recolor && isBrand(rgb)) return (color & 0xFF000000) | accent;
        if (amoled) {
            for (int dark : DARK) if (rgb == dark) return color & 0xFF000000;
        }
        return color;
    }
}
