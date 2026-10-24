package fr.phonemcp;
import java.util.Arrays;
public final class FramePixelsPolicyTest {
 public static void main(String[] a){int n=0;int[] p=new int[16];
  if(!FramePixelsPolicy.blank(p,4,4))throw new AssertionError("Transparent frame");n++;
  Arrays.fill(p,0xff000000);if(!FramePixelsPolicy.blank(p,4,4))throw new AssertionError("Black frame");n++;
  p[15]=0xff000004;if(FramePixelsPolicy.blank(p,4,4))throw new AssertionError("Dark visible detail must not be erased");n++;
  p[15]=0xff040000;if(FramePixelsPolicy.blank(p,4,4))throw new AssertionError("Red detail");n++;
  p[15]=0xff000400;if(FramePixelsPolicy.blank(p,4,4))throw new AssertionError("Green detail");n++;
  try{FramePixelsPolicy.blank(p,5,5);throw new AssertionError("Dimensions");}catch(IllegalArgumentException expected){n++;}
  System.out.println("PASS: "+n+" exact blank frame and visible-detail checks");
 }
}
