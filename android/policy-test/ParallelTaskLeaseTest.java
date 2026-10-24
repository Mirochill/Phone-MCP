package fr.phonemcp;

public final class ParallelTaskLeaseTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ParallelTaskLease leases=new ParallelTaskLease();String game="com.example.game";
        String[] onlyGame={game+"/.GameActivity"};
        expect(!leases.reclaim(game,45,0,8,onlyGame),"Never move an unowned primary task");
        leases.observe(game,45,0,8);expect(!leases.ownsTask(45),"A primary task is not ours");
        expect(!leases.owns(game),"An observation on the primary display cannot claim ownership");
        leases.observe(game,45,8,8);expect(leases.ownsTask(45),"Track the exact owned task for handoff");expect(!leases.ownsTask(46),"Never revoke a different human task");
        expect(leases.owns(game),"The app is owned after observation on the separate display");
        expect(leases.reclaim(game,45,0,8,onlyGame),"Return the same game task when the launcher moves it to primary");
        expect(!leases.reclaim(game,46,0,8,onlyGame),"A same-package duplicate task is not our task");
        expect(!leases.reclaim("com.example.other",45,0,8,onlyGame),"Matching task id never overrides package ownership");
        expect(!leases.reclaim(game,45,9,8,onlyGame),"Another external display is not primary");
        expect(!leases.reclaim(game,45,0,0,onlyGame),"The return destination can never be display zero");
        expect(!leases.reclaim(game,45,0,8,new String[]{game+"/.GameActivity","com.example.other/.Main"}),"Do not move a mixed root containing an unrelated app");
        expect(!leases.reclaim(game,45,0,8,null),"Unknown child tasks must fail closed");
        expect(!leases.reclaim(game,45,0,8,new String[]{null}),"An inaccessible child must fail closed");
        expect(leases.reclaim(game,45,0,8,new String[]{game}),"RootTaskInfo package-only child names remain supported");
        leases.observe("com.example.second",54,8,8);
        expect(leases.reclaim(game,45,0,8,onlyGame),"Switching AI apps retains existing task leases");
        leases.observe(game,50,8,8);
        expect(leases.reclaim(game,45,0,8,onlyGame),"A second owned task must not discard the first identity");
        expect(leases.reclaim(game,50,0,8,onlyGame),"Each task actually observed on the owned display may be recovered");
        leases.retain(java.util.Collections.singleton("com.example.second"));
        expect(!leases.owns(game),"Revoking app rights releases its task leases");
        expect(leases.owns("com.example.second"),"Revocation preserves an unrelated authorized app");
        leases.clear();
        expect(!leases.owns(game),"Stopping the session clears preview ownership");
        expect(!leases.reclaim(game,45,0,8,onlyGame),"An expired lease cannot recover a task");
        System.out.println("PASS: "+checks+" separate-display task ownership checks");
    }
}
