package fr.phonemcp;

/** A bounded pre-action wait; neither app launch nor input replay is part of recovery. */
final class WindowRecoveryPolicy {
    static final long WAIT_MS=4000,REFOCUS_MS=1500;
    static final int MAX_REFOCUS=2;
    private final long began;
    private long lastRefocus;
    private int attempts;
    WindowRecoveryPolicy(long began){if(began<0)throw new IllegalArgumentException("Invalid recovery clock");this.began=began;}
    boolean expired(long now){return now<began||now-began>=WAIT_MS;}
    boolean mayRefocus(long now){return !expired(now)&&attempts<MAX_REFOCUS&&(attempts==0||now-lastRefocus>=REFOCUS_MS);}
    void refocused(long now){if(!mayRefocus(now))throw new IllegalStateException("Recovery attempt is not authorized");attempts++;lastRefocus=now;}
    int attempts(){return attempts;}
    long elapsed(long now){return now<began?0:now-began;}
}
