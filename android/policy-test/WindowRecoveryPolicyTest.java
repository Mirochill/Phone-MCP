package fr.phonemcp;
public final class WindowRecoveryPolicyTest {
 private static int checks;
 private static void expect(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  WindowRecoveryPolicy p=new WindowRecoveryPolicy(1000);
  expect(!p.expired(1000),"New observation can recover");expect(p.mayRefocus(1000),"First exact-task recovery has no fixed delay");p.refocused(1000);
  expect(p.attempts()==1,"First recovery recorded");expect(!p.mayRefocus(1001),"No focus polling loop");expect(!p.mayRefocus(2499),"No premature second recovery");expect(p.mayRefocus(2500),"One delayed recovery is allowed");p.refocused(2500);
  expect(p.attempts()==2,"Second recovery recorded");expect(!p.mayRefocus(4000),"No third refocus");expect(!p.expired(4999),"Read can still arrive just before deadline");expect(p.expired(5000),"Exact deadline is bounded");expect(!p.mayRefocus(5000),"No recovery at deadline");
  expect(p.elapsed(1200)==200,"Monotonic duration");expect(p.expired(999),"Clock rollback fails closed");expect(p.elapsed(999)==0,"No negative diagnostic duration");
  boolean denied=false;try{p.refocused(5001);}catch(IllegalStateException expected){denied=true;}expect(denied,"Cannot bypass exhausted recovery budget");
  WindowRecoveryPolicy q=new WindowRecoveryPolicy(3000);expect(q.attempts()==0,"Other session has its own budget");expect(q.mayRefocus(3000),"Other session remains independent");q.refocused(3000);expect(q.attempts()==1&&p.attempts()==2,"Budgets cannot mix across agents");
  WindowRecoveryPolicy expired=new WindowRecoveryPolicy(0);expect(expired.expired(9000),"Long pause does not create unbounded retry");expect(!expired.mayRefocus(9000),"Expired state cannot focus a task");
  boolean negative=false;try{new WindowRecoveryPolicy(-1);}catch(IllegalArgumentException expected){negative=true;}expect(negative,"Invalid monotonic origin refused");
  System.out.println("PASS: "+checks+" bounded per-session window recovery checks");
 }
}
