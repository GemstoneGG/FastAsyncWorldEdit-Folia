package com.fastasyncworldedit.core.util;

import java.lang.reflect.Method;

public class FoliaUtil {

    private static final Boolean FOLIA_DETECTED = detectFolia();

    private static final Method IS_GLOBAL_TICK_THREAD = resolveIsGlobalTickThread();

    public static boolean isFoliaServer() {
        return FOLIA_DETECTED;
    }

    public static boolean isGlobalTickThread() {
        if (IS_GLOBAL_TICK_THREAD == null) {
            return false;
        }
        try {
            return (boolean) IS_GLOBAL_TICK_THREAD.invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Method resolveIsGlobalTickThread() {
        try {
            return Class.forName("org.bukkit.Bukkit").getMethod("isGlobalTickThread");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
