package fr.phonemcp;

public final class SessionPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){checks++;try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError(message);}
    public static void main(String[] args){
        long now=123456789L;
        long halfHour=SessionPolicy.deadline(now,30);
        expect(halfHour==now+1800000L,"Existing thirty-minute session must preserve its deadline");
        expect(SessionPolicy.active(true,halfHour-1,halfHour),"Finite session remains active before deadline");
        expect(!SessionPolicy.active(true,halfHour,halfHour),"Finite session stops exactly at deadline");
        expect(!SessionPolicy.active(true,halfHour+1,halfHour),"Expired finite session remains stopped");
        long day=SessionPolicy.deadline(now,24*60);
        expect(day>now+8*60*60000L,"A legitimate deadline beyond eight hours must be accepted");
        expect(SessionPolicy.validDeadline(now,day),"The privileged service must accept a 24-hour deadline");
        expect(SessionPolicy.deadline(now,0)==SessionPolicy.UNLIMITED,"Zero minutes explicitly selects unlimited duration");
        expect(SessionPolicy.unlimited(SessionPolicy.deadline(now,0)),"Unlimited mode must be distinguishable from a finite countdown");
        expect(!SessionPolicy.unlimited(halfHour),"Finite duration must not be shown as unlimited");
        expect(SessionPolicy.active(true,now+30L*24*60*60000,SessionPolicy.UNLIMITED),"Unlimited duration must survive more than eight hours and thirty days of uptime");
        expect(SessionPolicy.validDeadline(now,SessionPolicy.UNLIMITED),"Shizuku service must accept the explicit unlimited deadline");
        expect(!SessionPolicy.active(false,now,SessionPolicy.UNLIMITED),"Stopping the service always overrides unlimited duration");
        expect(!SessionPolicy.active(true,now,0),"The stopped sentinel never starts a session");
        expect(!SessionPolicy.validDeadline(now,now),"Reject an already expired privileged-service start");
        expect(!SessionPolicy.validDeadline(now,now-1),"Reject a past deadline");
        rejects(()->SessionPolicy.deadline(now,-1),"Negative duration must not become unlimited");
        rejects(()->SessionPolicy.deadline(-1,0),"Invalid clock must not create an unlimited session");
        rejects(()->SessionPolicy.deadline(now,Long.MAX_VALUE),"Multiplication overflow must not extend authorization");
        rejects(()->SessionPolicy.deadline(Long.MAX_VALUE-1000,1),"Deadline overflow must be rejected");
        rejects(()->SessionPolicy.deadline(Long.MAX_VALUE-60000,1),"Finite duration cannot silently become unlimited");
        System.out.println("PASS: "+checks+" session-duration regression checks");
    }
}
