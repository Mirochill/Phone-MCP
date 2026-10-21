package fr.phonemcp;

/** Monotonic session deadlines. Zero minutes is an explicit, locally selected unlimited session. */
final class SessionPolicy {
    static final long UNLIMITED = Long.MAX_VALUE;
    static long deadline(long now, long minutes) {
        if(now < 0 || minutes < 0)throw new IllegalArgumentException("Durée de session invalide");
        if(minutes == 0)return UNLIMITED;
        long end = Math.addExact(now, Math.multiplyExact(minutes, 60000L));
        if(end == UNLIMITED)throw new IllegalArgumentException("Durée de session trop longue");
        return end;
    }
    static boolean validDeadline(long now, long end) { return now >= 0 && end > now; }
    static boolean active(boolean running, long now, long end) { return running && validDeadline(now,end); }
    static boolean unlimited(long end) { return end == UNLIMITED; }
}
