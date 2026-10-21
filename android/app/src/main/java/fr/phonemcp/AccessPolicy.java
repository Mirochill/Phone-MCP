package fr.phonemcp;
import java.util.Map;
/** Shared capability defaults for screen control and notifications. */
final class AccessPolicy {
    static final String PREFIX = "grant.v4.";
    static boolean validPackage(String pkg) {
        return pkg != null && pkg.length() <= 240 && pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");
    }
    static boolean capability(String permission) {
        return "read".equals(permission) || "write".equals(permission)
                || "capture".equals(permission) || "notifications".equals(permission);
    }
    static String key(String pkg, String permission) { return PREFIX + pkg + "." + permission; }
    private static boolean value(Map<String, ?> grants, String key, boolean fallback) {
        return grants.containsKey(key) ? Boolean.TRUE.equals(grants.get(key)) : fallback;
    }
    static boolean allowed(String pkg, String permission, boolean installed, boolean defaultAll, Map<String, ?> grants) {
        return validPackage(pkg) && capability(permission) && installed
                && value(grants, key(pkg, "read"), defaultAll)
                && value(grants, key(pkg, permission), defaultAll);
    }
}
