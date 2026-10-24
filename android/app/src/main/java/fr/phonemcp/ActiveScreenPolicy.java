package fr.phonemcp;

/** Screen ownership follows live app sessions, never an open transport connection. */
final class ActiveScreenPolicy {
    private int count;
    private long expires;
    private long revision;
    void update(int sessions,long expiresElapsedMs,long now) {
        if(sessions<0||sessions>4)throw new IllegalArgumentException("Invalid active session count.");
        if(sessions>0&&expiresElapsedMs<=now)throw new IllegalArgumentException("Screen ownership must have a future session deadline.");
        count=sessions;expires=sessions==0?0:expiresElapsedMs;revision++;
    }
    boolean active(long now) { return count>0&&now<expires; }
    int count() { return count; }
    long expires() { return expires; }
    long revision() { return revision; }
    void clear() { count=0;expires=0;revision++; }
}