package fr.phonemcp;
import java.util.*;
public final class ConditionalPlanPolicyTest {
 static int tests;static void ok(boolean b,String m){tests++;if(!b)throw new AssertionError(m);}
 static void fail(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed,"invalid move rejected");}
 public static void main(String[] args){
  ConditionalGate g=new ConditionalGate(80,1000);ok(g.update(0,-1,"",true,0)==-1,"wait baseline");ok(g.update(1,3,"one",false,1)==-1,"never act first observation");ok(g.update(1,3,"one",false,80)==-1,"wait stability");ok(g.update(1,3,"one",false,81)==3,"one exact stable rule");g.reset();ok(g.update(2,3,"one",false,0)==-3,"ambiguity never picks first");ok(g.update(0,-1,"",false,0)==-1,"transition grace");ok(g.update(0,-1,"",false,1000)==-2,"unknown yields to model");g.reset();ok(g.update(1,1,"A",false,0)==-1,"candidate A");ok(g.update(1,2,"B",false,90)==-1,"changed candidate resets");ok(g.update(1,2,"B",false,170)==2,"stable B");
  String start="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",e4=ChessPosition.move(start,"e2e4");ok(e4.equals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"),"exact e4");String e5=ChessPosition.move(e4,"e7e5");ok(ChessPosition.move(e5,"g1f3").equals("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R"),"exact Nf3");fail(()->ChessPosition.move(start,"e3e4"));fail(()->ChessPosition.move(start,"e1e2"));fail(()->ChessPosition.move(start,"e1g1"));fail(()->ChessPosition.move(start,"a7a8q"));
  List<ChessPosition.Cell> cells=new ArrayList<>();Map<String,Character> pieces=ChessPosition.decode(start);Map<Character,String> names=Map.of('r',"Tour",'n',"Cavalier",'b',"Fou",'q',"Dame",'k',"Roi",'p',"Pion");
  for(String sq:pieces.keySet()){char p=pieces.get(sq);int x=sq.charAt(0)-'a',y=8-(sq.charAt(1)-'0');String label=p=='.'?sq+", vide":(Character.isUpperCase(p)?"Blancs ":"Noirs ")+names.get(Character.toLowerCase(p))+" en "+sq;cells.add(new ChessPosition.Cell(label,x*90,252+y*90,(x+1)*90,342+y*90));}
  ok(ChessPosition.read(cells).fen.equals(start),"all64 observations parsed");cells.add(cells.get(0));ok(ChessPosition.read(cells)==null,"duplicates refused");cells.remove(cells.size()-1);cells.remove(0);ok(ChessPosition.read(cells)==null,"partial board refused");System.out.println("CONDITIONAL_PLAN_POLICY_VERIFIED: "+tests);
 }
}
