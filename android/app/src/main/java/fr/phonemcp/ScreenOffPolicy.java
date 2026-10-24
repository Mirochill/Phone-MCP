package fr.phonemcp;

/** Physical-screen intent is independent of a virtual display's cached power state. */
final class ScreenOffPolicy {
    private boolean off;
    private long revision;
    private long maskedWakeUntil,maskedWakeRevision=-1;
    boolean isOff() { return off; }
    long revision() { return revision; }
    void screenOff() { off = true; revision++; maskedWakeUntil=0; }
    void userRestore() { off = false; revision++; maskedWakeUntil=0; }
    // SCREEN_ON may be a real human even immediately after an AI command. Never discard it
    // based on timing alone: Android does not expose a trusted wake reason through this API.
    boolean mayReinforce(long expectedRevision) { return off && revision == expectedRevision; }
    static final int SCREEN_EVENT_IGNORED=0,SCREEN_EVENT_OFF=1,SCREEN_EVENT_ON=2;
    static int resolvePhysicalEvent(boolean reportedOn,boolean userPresent,Boolean primaryInteractive) {
        // USER_PRESENT is a separate local takeover signal. Missing OEM/API information
        // must not trap the owner behind our blank; preserve the previous event behavior.
        if(userPresent)return SCREEN_EVENT_ON;
        if(primaryInteractive!=null&&primaryInteractive.booleanValue()!=reportedOn)return SCREEN_EVENT_IGNORED;
        return reportedOn?SCREEN_EVENT_ON:SCREEN_EVENT_OFF;
    }
    void beginMaskedWake(long now) { if(!off)throw new IllegalStateException("Dalle noire non autorisée."); maskedWakeRevision=revision;maskedWakeUntil=now+2000; }
    boolean consumeMaskedWake(long now) { boolean ours=off&&maskedWakeRevision==revision&&now<=maskedWakeUntil&&maskedWakeUntil>0;maskedWakeUntil=0;return ours; }
    void cancelMaskedWake() { maskedWakeUntil=0; }
    static boolean mayHoldScreenLease(int groupId,boolean physicalBlank){return groupId>0||physicalBlank;}
    static boolean mayWakeBehindBlank(int displayId,int groupId,boolean blankOwned) { return displayId>0&&groupId==0&&blankOwned; }
    static boolean powerButtonRequestsRestore(boolean blankOwned,boolean heldAwake,Boolean primaryInteractive,int sleepReason) {
        return blankOwned&&heldAwake&&Boolean.FALSE.equals(primaryInteractive)&&sleepReason==4;
    }
    static long physicalPanel(long[] ids,Long defaultId) {
        if(ids==null||ids.length==0)throw new IllegalStateException("Aucune dalle physique disponible.");
        if(defaultId!=null){
            for(long id:ids)if(id==defaultId.longValue())return id;
            throw new IllegalStateException("Dalle physique principale absente.");
        }
        if(ids.length==1)return ids[0];
        throw new IllegalStateException("Dalle physique principale non identifiée. Aucun écran modifié.");
    }
    static boolean mayWakeDisplayGroup(int displayId, int groupId) {
        return displayId > 0 && groupId > 0;
    }
}

