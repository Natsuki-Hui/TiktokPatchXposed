package com.golda.patchertiktok;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Invisible mode. TikTok reports a story view with its regular play statistics (marked with story
 * fields), a profile visit with a separate call, and, with view history on, video views of people
 * you follow through the same statistics. Those requests are cancelled before they leave, so the
 * author never sees the view; the story, profile or video itself loads as usual.
 */
final class Ghost {
    private static final String INTERCEPTOR = "com.bytedance.retrofit2.CallServerInterceptor";
    private static final String REQUEST = "com.bytedance.retrofit2.client.Request";
    private static final String STATS = "/aweme/v1/aweme/stats/";
    private static final String PROFILE_VIEW = "/profile/view_record/add";
    private static final String CHECKED = "com.golda.patchertiktok.ghostChecked";

    private Ghost() { }

    static void install(ClassLoader loader) {
        Class<?> interceptor = Hooks.findClass(INTERCEPTOR, loader);
        Class<?> request = Hooks.findClass(REQUEST, loader);
        if (interceptor == null || request == null) {
            RuntimeLog.log("invisible mode unavailable: network classes not found");
            return;
        }
        int hooks = 0;
        for (Method method : interceptor.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            int index = -1;
            for (int i = 0; i < p.length; i++) if (p[i] == request) index = i;
            if (index < 0) continue;
            int at = index;
            Hooks.hook(method, new Hooks.Hook() {
                @Override protected void before(Hooks.Call param) {
                    if (!Prefs.on(Prefs.GHOST_STORIES) && !Prefs.on(Prefs.GHOST_PROFILE) && !Prefs.on(Prefs.GHOST_POSTS)) return;
                    Object req = param.args[at];
                    // The interceptor's helpers pass the same request along; decide once.
                    if (req == null || Hooks.getExtra(req, CHECKED) != null) return;
                    Hooks.setExtra(req, CHECKED, Boolean.TRUE);
                    if (reveals(req)) param.setThrowable(new IOException("cancelled by TiktokPatchXposed"));
                }
            });
            hooks++;
        }
        RuntimeLog.log("invisible mode: hooks=" + hooks);
    }

    /** Whether the request reports a view the author would see. */
    private static boolean reveals(Object request) {
        Object value = Reflect.call(request, "getPath");
        if (!(value instanceof String)) return false;
        String path = (String) value;
        if (Prefs.on(Prefs.GHOST_PROFILE) && path.contains(PROFILE_VIEW)) return true;
        if (!path.contains(STATS)) return false;
        String body = "&" + body(request);
        if (Prefs.on(Prefs.GHOST_STORIES)
                && (body.contains("&story_consumption_type=") || body.contains("&user_story_version="))) return true;
        // With view history on, the people you follow see which of their videos you watched
        // (follow_status 1 = following, 2 = friends).
        return Prefs.on(Prefs.GHOST_POSTS) && (body.contains("&follow_status=1") || body.contains("&follow_status=2"));
    }

    private static String body(Object request) {
        Object body = Reflect.call(request, "getBody");
        if (body == null) return "";
        try {
            Method write = body.getClass().getMethod("writeTo", OutputStream.class);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write.invoke(body, out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            return "";
        }
    }
}
