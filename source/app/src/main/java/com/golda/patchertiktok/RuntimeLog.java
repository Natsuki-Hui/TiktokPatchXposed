package com.golda.patchertiktok;

final class RuntimeLog {
    private RuntimeLog() { }

    static void log(String message) {
        FeatureStatus.observe(message);
        Hooks.log(message);
    }
}
