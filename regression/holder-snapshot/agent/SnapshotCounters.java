package audit.holder;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Isolated test only: count the original core calls without changing their results. */
public final class SnapshotCounters {
    private static final ConcurrentHashMap<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    public static void holder(boolean snapshot) { record(snapshot ? "holder.true" : "holder.false"); }
    public static void record(String kind) {
        increment("total." + kind);
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().equals("net.skinsrestorer.bukkit.listener.InventoryListener") && frame.getMethodName().equals("onInventoryClick")) {
                increment("sr." + kind);
                increment(frame.getClassName() + "#" + frame.getMethodName() + "." + kind);
                return;
            }
        }
    }
    private static void increment(String key) { COUNTS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet(); }
    public static void reset() { COUNTS.clear(); }
    public static Map<String, Long> counts() {
        Map<String, Long> snapshot = new TreeMap<>();
        COUNTS.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }
}
