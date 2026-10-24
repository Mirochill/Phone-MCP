package fr.phonemcp;
public final class ScreenAwakePreferenceTest{
 static int checks;static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[]args){
  ScreenAwakePreference p=new ScreenAwakePreference();ok(!p.keepOn(),"New installations default to physical screen keep-on disabled");ok(p.revision()==-1,"No received local setting yet");ok(p.select(false,0),"Saved default accepted");ok(!p.select(false,0),"Same setting delivery is idempotent");ok(!p.keepOn(),"Default cannot opt into awake fallback");
  ok(p.select(true,1)&&p.keepOn(),"Only local enabled choice selects keep-on");ok(p.select(false,2)&&!p.keepOn(),"User disables while commands are in flight");ok(!p.select(true,1)&&!p.keepOn(),"Late startup or old enable cannot override new disable");ok(!p.select(false,2),"Duplicate disable does not regress revision");
  try{p.select(true,2);throw new AssertionError("Conflicting delivery accepted");}catch(SecurityException expected){ok(!p.keepOn(),"Conflicting version cannot change choice");}
  try{p.select(true,-1);throw new AssertionError("Negative revision accepted");}catch(IllegalArgumentException expected){ok(!p.keepOn(),"Invalid revision cannot change choice");}
  ok(p.select(true,3)&&p.keepOn(),"Later local enable remains possible");ok(!p.select(false,0)&&p.keepOn(),"Late default cannot override later user enable");ok(p.select(false,4),"Last local disable honored");
  for(int group:new int[]{-1,0,1}){ok(!ScreenOffPolicy.mayHoldScreenLease(group,false)||group>0,"Lit primary must not retain a shared/unknown display screen wake lock");ok(ScreenOffPolicy.mayHoldScreenLease(group,true),"A separately blanked panel permits virtual input wake ownership");}
  System.out.println("PASS: "+checks+" local screen preference ordering and shared-lease checks");
 }
}
