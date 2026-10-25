package fr.phonemcp;
public class PlanGraphPolicyTest {
 static int tests;static void good(int[] a,int[] b){PlanGraphPolicy.validate(a,b);tests++;}static void bad(int[] a,int[] b){try{PlanGraphPolicy.validate(a,b);}catch(IllegalArgumentException e){tests++;return;}throw new AssertionError("Invalid graph accepted");}
 public static void main(String[] args){good(new int[]{0,0,1,1,2},new int[]{1,2,-1,-1,-1});good(new int[]{0,1,2,3,4,5},new int[]{1,2,3,4,5,-1});bad(new int[]{0,1,2,3,4,5,6},new int[]{1,2,3,4,5,6,-1});bad(new int[]{0},new int[]{0});bad(new int[]{0},new int[]{1});bad(new int[]{1},new int[]{-1});bad(new int[]{0,0,1},new int[]{1,1,-1});bad(new int[]{0,0,0,0,0,0,0,0,0},new int[]{-1,-1,-1,-1,-1,-1,-1,-1,-1});System.out.println("PLAN_GRAPH_POLICY_VERIFIED: "+tests);}
}
