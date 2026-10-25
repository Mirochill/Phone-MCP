package fr.phonemcp;

import java.util.*;
/** Exact accessible board representation, not an engine or a legality evaluator. */
final class ChessPosition {
    static final class Cell {
        final String label;final int l,t,r,b;
        Cell(String label,int l,int t,int r,int b){this.label=label;this.l=l;this.t=t;this.r=r;this.b=b;}
    }
    static final class Board {
        final String fen,geometry;final Map<String,Cell> cells;final Map<String,Character> pieces;
        Board(String f,String g,Map<String,Cell> c,Map<String,Character> p){fen=f;geometry=g;cells=c;pieces=p;}
    }
    static final java.util.regex.Pattern EMPTY=java.util.regex.Pattern.compile("^([a-h][1-8]),\\s*(?:vide|empty)$",2);
    static final java.util.regex.Pattern PIECE=java.util.regex.Pattern.compile("^(White|Black|Blancs|Noirs) (Pawn|Pion|Rook|Tour|Knight|Cavalier|Bishop|Fou|Queen|Dame|King|Roi) (?:on|en) ([a-h][1-8])$",2);
    static Board read(List<Cell> source){
        TreeMap<String,Cell> cells=new TreeMap<>();TreeMap<String,Character> pieces=new TreeMap<>();
        for(Cell c:source){String label=c.label==null?"":c.label.trim(),square=null;char piece='.';java.util.regex.Matcher m=EMPTY.matcher(label);
            if(m.matches())square=m.group(1).toLowerCase(Locale.ROOT);else{m=PIECE.matcher(label);if(m.matches()){square=m.group(3).toLowerCase(Locale.ROOT);String name=m.group(2).toLowerCase(Locale.ROOT);piece=name.matches("pawn|pion")?'p':name.matches("rook|tour")?'r':name.matches("knight|cavalier")?'n':name.matches("bishop|fou")?'b':name.matches("queen|dame")?'q':'k';if(m.group(1).matches("(?i)white|blancs"))piece=Character.toUpperCase(piece);}}
            if(square==null)continue;if(cells.containsKey(square)||c.l<0||c.t<0||c.r>720||c.b>1280||c.l>=c.r||c.t>=c.b)return null;cells.put(square,c);pieces.put(square,piece);
        }
        if(cells.size()!=64)return null;Cell a=cells.get("a8"),h=cells.get("h8"),a1=cells.get("a1");double ax=(a.l+a.r)/2.0,ay=(a.t+a.b)/2.0,dx=((h.l+h.r)/2.0-ax)/7,dy=((a1.t+a1.b)/2.0-ay)/7;
        if(Math.abs(dx)<8||Math.abs(dy)<8||Math.abs(Math.abs(dx)-Math.abs(dy))>3)return null;StringBuilder geometry=new StringBuilder();
        for(Map.Entry<String,Cell> e:cells.entrySet()){String q=e.getKey();Cell c=e.getValue();if(Math.abs((c.l+c.r)/2.0-(ax+(q.charAt(0)-'a')*dx))>2||Math.abs((c.t+c.b)/2.0-(ay+(8-(q.charAt(1)-'0'))*dy))>2||Math.abs(c.r-c.l-Math.abs(dx))>3||Math.abs(c.b-c.t-Math.abs(dy))>3)return null;geometry.append(q).append(':').append(c.l).append(',').append(c.t).append(',').append(c.r).append(',').append(c.b).append(';');}
        return new Board(encode(pieces),geometry.toString(),cells,pieces);
    }
    static Map<String,Character> decode(String fen){
        if(fen==null||fen.length()>90)throw new IllegalArgumentException("Invalid piece placement");String[] rows=fen.split("/",-1);if(rows.length!=8)throw new IllegalArgumentException("Eight ranks required");Map<String,Character> cells=new TreeMap<>();
        for(int y=0;y<8;y++){int x=0;for(char p:rows[y].toCharArray()){int count=p>='1'&&p<='8'?p-'0':1;if(!(p>='1'&&p<='8')&&"prnbqkPRNBQK".indexOf(p)<0)throw new IllegalArgumentException("Invalid piece");for(int i=0;i<count;i++){if(x>=8)throw new IllegalArgumentException("Rank too long");cells.put(""+(char)('a'+x++)+(8-y),p>='1'&&p<='8'?'.':p);}}if(x!=8)throw new IllegalArgumentException("Rank too short");}return cells;
    }
    static String encode(Map<String,Character> pieces){StringBuilder out=new StringBuilder();for(int y=8;y>=1;y--){if(y<8)out.append('/');int empty=0;for(char x='a';x<='h';x++){char p=pieces.get(""+x+y);if(p=='.')empty++;else{if(empty>0)out.append(empty);empty=0;out.append(p);}}if(empty>0)out.append(empty);}return out.toString();}
    static String move(String fen,String uci){
        if(uci==null||!uci.matches("[a-h][1-8][a-h][1-8]"))throw new IllegalArgumentException("Four-character move required");Map<String,Character> p=decode(fen);String from=uci.substring(0,2),to=uci.substring(2);char piece=p.get(from),target=p.get(to);
        if(from.equals(to)||piece=='.'||(target!='.'&&Character.isUpperCase(piece)==Character.isUpperCase(target)))throw new IllegalArgumentException("Invalid observed source/target");
        if((Character.toLowerCase(piece)=='k'&&Math.abs(from.charAt(0)-to.charAt(0))>1)||(Character.toLowerCase(piece)=='p'&&(to.charAt(1)=='1'||to.charAt(1)=='8'||(from.charAt(0)!=to.charAt(0)&&target=='.'))))throw new IllegalArgumentException("Special move needs a separate observed decision");
        p.put(from,'.');p.put(to,piece);return encode(p);
    }
}
