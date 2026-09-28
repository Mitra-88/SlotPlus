package me.mitra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event-driven debug logging behind the verboseLogging config toggle. Only ever
 * called from input handlers and state transitions - never from per-frame
 * render paths.
 */
final class SlotPlusLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("SlotPlus");
    private static volatile boolean enabled = true;

    private SlotPlusLog() {
    }

    static void configure(boolean verbose) {
        enabled = verbose;
    }

    static void info(String format, Object... args) {
        if (enabled) LOGGER.info(format, args);
    }

    static void warn(String format, Object... args) {
        if (enabled) LOGGER.warn(format, args);
    }
}
