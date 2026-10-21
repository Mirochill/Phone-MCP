package fr.phonemcp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayDeque;

final class Audit {
    private static final ArrayDeque<String> entries = new ArrayDeque<>();
    static synchronized void add(String operation,boolean ok) {
        String line = new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date()) + " · " + operation + " · " + (ok ? "OK" : "refus/échec");
        entries.addFirst(line); while (entries.size() > 8) entries.removeLast();
    }
    static synchronized String latest() { return String.join("\n",entries); }
}
