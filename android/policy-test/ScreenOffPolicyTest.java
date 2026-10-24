package fr.phonemcp;

public final class ScreenOffPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ScreenOffPolicy policy=new ScreenOffPolicy();
        expect(!policy.isOff(),"Do not assume that a new physical screen is off");
        policy.screenOff();long first=policy.revision();
        expect(policy.isOff(),"Retain physical intent separately from display cache");
        expect(policy.mayReinforce(first),"Only the current off intent may reinforce");
        policy.userRestore();
        expect(!policy.isOff(),"Local restore immediately relinquishes off intent");
        expect(!policy.mayReinforce(first),"An already queued reinforcement cannot reblank a user wake");
        policy.screenOff();long second=policy.revision();
        expect(second!=first,"A new screen-off cycle has a different identity");
        expect(!policy.mayReinforce(first),"Old callbacks cannot act in a later off cycle");
        expect(policy.mayReinforce(second),"New off cycle may reinforce itself");
        policy.userRestore();
        expect(!policy.mayReinforce(second),"An immediate SCREEN_ON wins without any blind timing window");
        policy.userRestore();expect(!policy.isOff(),"Repeated screen-on observations remain idempotent");
        expect(!ScreenOffPolicy.mayWakeDisplayGroup(0,0),"Never wake physical display zero");
        expect(!ScreenOffPolicy.mayWakeDisplayGroup(0,1),"A nondefault group cannot authorize physical display zero");
        expect(!ScreenOffPolicy.mayWakeDisplayGroup(-1,1),"An absent display cannot wake a group");
        expect(!ScreenOffPolicy.mayWakeDisplayGroup(40,0),"A nonzero virtual ID does not imply its own group");
        expect(!ScreenOffPolicy.mayWakeDisplayGroup(40,-1),"Unknown display groups fail closed");
        expect(ScreenOffPolicy.mayWakeDisplayGroup(40,2),"A known independent group may wake its own display");
        expect(ScreenOffPolicy.physicalPanel(new long[]{22},null)==22,"A single panel is an unambiguous fallback");
        expect(ScreenOffPolicy.physicalPanel(new long[]{22,33},33L)==33,"Only the identified default output may be blanked");
        expect(ScreenOffPolicy.physicalPanel(new long[]{-22,33},-22L)==-22,"Physical IDs are signed 64-bit values");
        boolean missing=false;try{ScreenOffPolicy.physicalPanel(new long[]{22,33},44L);}catch(IllegalStateException expected){missing=true;}
        expect(missing,"Never substitute an external screen when the main panel disappeared");
        boolean ambiguous=false;try{ScreenOffPolicy.physicalPanel(new long[]{22,33},null);}catch(IllegalStateException expected){ambiguous=true;}
        expect(ambiguous,"Unknown multiple outputs fail closed without blanking any panel");
        boolean absent=false;try{ScreenOffPolicy.physicalPanel(null,null);}catch(IllegalStateException expected){absent=true;}
        expect(absent,"An absent panel cannot be powered");
        policy.screenOff();long silentRevision=policy.revision();
        int virtualWake=ScreenOffPolicy.resolvePhysicalEvent(true,false,Boolean.FALSE);
        expect(virtualWake==ScreenOffPolicy.SCREEN_EVENT_IGNORED,"A global wake from another group cannot stand for primary wake");
        if(virtualWake==ScreenOffPolicy.SCREEN_EVENT_ON)policy.userRestore();
        expect(policy.isOff()&&policy.revision()==silentRevision,"Rejected virtual wake keeps silence intent and its revision");
        expect(policy.mayReinforce(silentRevision),"Rejected virtual wake does not cancel the current off intent");
        int physicalWake=ScreenOffPolicy.resolvePhysicalEvent(true,false,Boolean.TRUE);
        expect(physicalWake==ScreenOffPolicy.SCREEN_EVENT_ON,"An interactive primary power-button wake is accepted immediately");
        if(physicalWake==ScreenOffPolicy.SCREEN_EVENT_ON)policy.userRestore();
        expect(!policy.isOff()&&!policy.mayReinforce(silentRevision),"Verified primary wake wins over already queued blanking");
        int staleSleep=ScreenOffPolicy.resolvePhysicalEvent(false,false,Boolean.TRUE);
        expect(staleSleep==ScreenOffPolicy.SCREEN_EVENT_IGNORED,"An old/global OFF cannot silence an interactive primary screen");
        if(staleSleep==ScreenOffPolicy.SCREEN_EVENT_OFF)policy.screenOff();
        expect(!policy.isOff(),"Rejected global OFF leaves local takeover intact");
        expect(ScreenOffPolicy.resolvePhysicalEvent(false,false,Boolean.FALSE)==ScreenOffPolicy.SCREEN_EVENT_OFF,"A verified primary sleep is accepted");
        expect(ScreenOffPolicy.resolvePhysicalEvent(true,true,Boolean.FALSE)==ScreenOffPolicy.SCREEN_EVENT_ON,"USER_PRESENT cannot be blocked by an interactivity race");
        expect(ScreenOffPolicy.resolvePhysicalEvent(false,true,Boolean.FALSE)==ScreenOffPolicy.SCREEN_EVENT_ON,"USER_PRESENT remains a takeover even if the bridge's level sample was stale");
        expect(ScreenOffPolicy.resolvePhysicalEvent(true,false,null)==ScreenOffPolicy.SCREEN_EVENT_ON,"Unavailable OEM API preserves a possible human wake");
        expect(ScreenOffPolicy.resolvePhysicalEvent(false,false,null)==ScreenOffPolicy.SCREEN_EVENT_OFF,"Unavailable OEM API preserves a reported sleep");
        expect(ScreenOffPolicy.resolvePhysicalEvent(false,true,null)==ScreenOffPolicy.SCREEN_EVENT_ON,"USER_PRESENT works without a hidden per-display API");
        expect(ScreenOffPolicy.resolvePhysicalEvent(true,false,Boolean.TRUE)==ScreenOffPolicy.SCREEN_EVENT_ON,"A real shared-group application wake remains unclassified, not falsely prevented");
        expect(!ScreenOffPolicy.mayWakeBehindBlank(0,0,true),"Never authorize input-display zero for masked work");
        expect(!ScreenOffPolicy.mayWakeBehindBlank(40,0,false),"A shared wake requires owned blank intent");
        expect(!ScreenOffPolicy.mayWakeBehindBlank(40,-1,true),"Unknown group cannot use the shared fallback");
        expect(ScreenOffPolicy.mayWakeBehindBlank(40,0,true),"Shared Android can wake behind the owned blank");
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(true,true,false,2),"Timeout is not a local power-button takeover");
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(false,true,false,4),"Power button on a visible screen requests sleep");
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(true,false,false,4),"An old sleep reason without our awake blank is not a takeover");
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(true,true,null,4),"Primary sleep must be verified");
        expect(ScreenOffPolicy.powerButtonRequestsRestore(true,true,false,4),"Local power button returns a held-awake black panel");
        policy.screenOff();policy.beginMaskedWake(1000);
        expect(policy.consumeMaskedWake(1100),"Our observed wake keeps blank intent");
        expect(!policy.consumeMaskedWake(1101),"Internal wake is consumed once");
        policy.beginMaskedWake(1200);policy.userRestore();
        expect(!policy.consumeMaskedWake(1300),"Local takeover invalidates a pending internal wake");
        policy.screenOff();policy.beginMaskedWake(1400);
        expect(!policy.consumeMaskedWake(3401),"An unobserved internal wake cannot hide a future owner wake");
        policy.screenOff();policy.beginMaskedWake(4000);policy.cancelMaskedWake();
        expect(!policy.consumeMaskedWake(4001),"No session owner leaves an internal wake able to consume a later human wake");
        boolean held=true,blank=true;
        expect(ScreenOffPolicy.powerButtonRequestsRestore(blank,held,false,4),"Command loop detects local POWER before its queued OFF broadcast");
        policy.userRestore();held=false;blank=policy.isOff();
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(blank,held,false,4),"Later queued OFF cannot perform a second restore");
        expect(!policy.mayReinforce(policy.revision()),"No pending reinforcement may blank after local recovery");
        expect(!ScreenOffPolicy.powerButtonRequestsRestore(true,false,false,4),"Initial manual sleep before first internal wake remains dark");
        System.out.println("PASS: "+checks+" physical-screen intent and power-group checks");
    }
}

