package fr.phonemcp;
/** Pure matching gate. No input is sent by this class; unknown/ambiguous cases never select a rule. */
final class ConditionalGate {
    static final int WAIT=-1,UNKNOWN=-2,AMBIGUOUS=-3;
    private String pending="";private long since,unknownAt=-1;private final long stable,unknownGrace;
    ConditionalGate(long stable,long unknownGrace){if(stable<50||stable>500||unknownGrace<100||unknownGrace>3000)throw new IllegalArgumentException("Unbounded gate");this.stable=stable;this.unknownGrace=unknownGrace;}
    int update(int matches,int index,String key,boolean waiting,long now){
        if(matches>1)return AMBIGUOUS;
        if(matches==0){pending="";if(waiting){unknownAt=-1;return WAIT;}if(unknownAt<0)unknownAt=now;return now-unknownAt>=unknownGrace?UNKNOWN:WAIT;}
        unknownAt=-1;if(!key.equals(pending)){pending=key;since=now;return WAIT;}return now-since>=stable?index:WAIT;
    }
    long pendingSince(){return since;}
    void reset(){pending="";since=0;unknownAt=-1;}
}
