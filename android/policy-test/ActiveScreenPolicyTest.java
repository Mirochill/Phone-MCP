package fr.phonemcp;

public final class ActiveScreenPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ActiveScreenPolicy policy=new ActiveScreenPolicy();
        expect(!policy.active(0),"A connected service with no app cannot hold the screen");
        policy.update(1,2000,1000);long first=policy.revision();
        expect(policy.active(1000),"First app session requires the physical screen awake");
        expect(policy.active(1999),"Pauses retain ownership through the app lease");
        expect(!policy.active(2000),"Lease expiry releases ownership independently of transport");
        policy.update(2,4000,1500);
        expect(policy.revision()!=first,"An old expiry callback cannot release renewed ownership");
        expect(policy.count()==2&&policy.expires()==4000,"Multiple sessions share one bounded screen lease");
        policy.update(1,3500,2000);
        expect(policy.active(3000),"Ending one app leaves the other app awake");
        expect(!policy.active(3500),"Shortened final deadline is honored");
        policy.update(1,6000,3500);policy.update(0,0,4000);
        expect(!policy.active(4000)&&policy.count()==0,"Last app end releases immediately");
        policy.update(1,8000,6000);policy.clear();
        expect(!policy.active(6001)&&policy.expires()==0,"Stop and disconnect release ownership");
        int rejected=0;for(int count:new int[]{-1,5})try{policy.update(count,10000,9000);}catch(IllegalArgumentException expected){rejected++;}
        expect(rejected==2,"Invalid session counts cannot claim the screen");
        try{policy.update(1,10000,10000);throw new AssertionError("Expired ownership accepted");}catch(IllegalArgumentException expected){checks++;}
        policy.update(0,Long.MAX_VALUE,10000);
        expect(!policy.active(10000)&&policy.expires()==0,"Connection deadlines without app sessions cannot hold the screen");
        System.out.println("PASS: "+checks+" live app session screen-ownership checks");
    }
}