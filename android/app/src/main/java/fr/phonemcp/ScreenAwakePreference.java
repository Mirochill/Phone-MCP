package fr.phonemcp;
/** Ordered preference received only over the APK-owned settings Binder method. */
final class ScreenAwakePreference {
 private boolean keepOn;
 private long revision=-1;
 boolean keepOn(){return keepOn;}
 long revision(){return revision;}
 boolean select(boolean value,long next){
  if(next<0)throw new IllegalArgumentException("Invalid screen setting revision.");
  if(next<revision)return false;
  if(next==revision){if(value!=keepOn)throw new SecurityException("Conflicting screen setting revision.");return false;}
  keepOn=value;revision=next;return true;
 }
}
