package fr.phonemcp;

public final class ParallelGesturePolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String epoch="11a11111-1111-4111-8111-111111111111",session="22b22222-2222-4222-8222-222222222222",request="33c33333-3333-4333-8333-333333333333",pkg="fr.phonemcp.testfixture";
        expect(ParallelGesturePolicy.target(epoch,session,pkg,67,100,720,1280,6000,1000),"A bounded authorized offscreen target is valid");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,0,100,720,1280,6000,1000),"Display zero can never reach app gesture dispatch");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,-1,100,720,1280,6000,1000),"Invalid display cannot fall back to the focused/default display");
        expect(!ParallelGesturePolicy.target("stale",session,pkg,67,100,720,1280,6000,1000),"An absent epoch cannot authorize a callback");
        expect(!ParallelGesturePolicy.target(epoch,"other",pkg,67,100,720,1280,6000,1000),"A malformed session cannot be reused");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,67,-1,720,1280,6000,1000),"An unknown window never accepts coordinates");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,67,100,0,1280,6000,1000),"Missing dimensions cannot be guessed");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,67,100,720,1280,1000,1000),"An expired queued gesture is refused before dispatch");
        expect(!ParallelGesturePolicy.target(epoch,session,pkg,67,100,720,1280,8000,1000),"Unbounded future dispatch is refused");
        expect(ParallelGesturePolicy.stroke(191,527,191,527,0,60,720,1280),"The real fixture button coordinates are representable exactly");
        expect(ParallelGesturePolicy.stroke(191,527,191,527,0,1800,720,1280),"Existing long-press capability is preserved");
        expect(!ParallelGesturePolicy.stroke(720,527,720,527,0,60,720,1280),"Right edge is outside the display");
        expect(!ParallelGesturePolicy.stroke(191,-1,191,527,0,60,720,1280),"Negative coordinates are refused");
        expect(!ParallelGesturePolicy.stroke(191,527,191,527,0,2001,720,1280),"Every accepted gesture has a short finite upper bound");
        expect(!ParallelGesturePolicy.stroke(191,527,191,527,501,60,720,1280),"Future repeated taps cannot be scheduled indefinitely");
        expect(ParallelGesturePolicy.resultMatches(request,67,request,67),"A result must match the exact request and display");
        expect(!ParallelGesturePolicy.resultMatches(request,67,session,67),"Another request completion cannot complete this action");
        expect(!ParallelGesturePolicy.resultMatches(request,67,request,68),"Another agent display cannot complete this action");
        expect(!ParallelGesturePolicy.resultMatches(request,0,request,0),"Even a matching callback never permits display zero");
        System.out.println("PASS: "+checks+" bounded parallel gesture channel checks");
    }
}
