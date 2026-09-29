package me.mitra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event-driven debug logging behind the verboseLogging config toggle. Only ever
 * called from input handlers and state transitions - never from per-frame
 * render paths. Consecutive identical lines collapse into a repetition count
 * instead of flooding the log.
 */
final class SlotPlusLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("SlotPlus");
    private static volatile boolean enabled = true;
    private static String lastMessage;
    private static int repeats;

    private SlotPlusLog() {
    }

    static void configure(boolean verbose) {
        enabled = verbose;
    }

    static void info(String format, Object... args) {
        if (!enabled) return;
        String message = format(format, args);
        if (message.equals(lastMessage)) {
            repeats++;
            return;
        }
        flushRepeats();
        lastMessage = message;
        LOGGER.info(message);
    }

    static void warn(String format, Object... args) {
        if (!enabled) return;
        flushRepeats();
        lastMessage = null;
        LOGGER.warn(format(format, args));
    }

    private static void flushRepeats() {
        if (repeats > 0) {
            LOGGER.info("(previous line repeated {} times)", repeats);
            repeats = 0;
        }
    }

    private static String format(String format, Object... args) {
        if (args.length == 0) return format;
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Object arg : args) {
            int placeholder = format.indexOf("{}", index);
            if (placeholder == -1) break;
            builder.append(format, index, placeholder).append(arg);
            index = placeholder + 2;
        }
        return builder.append(format.substring(index)).toString();
    }
}
