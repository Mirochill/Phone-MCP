package fr.phonemcp;

import java.util.List;

/** Select a requested app's window without treating system decorations as blockers. */
final class WindowPolicy {
    static final class Info {
        final int id, layer;
        final String pkg;
        final boolean active, focused;
        Info(int id, String pkg, int layer, boolean active, boolean focused) {
            this.id=id; this.pkg=pkg; this.layer=layer; this.active=active; this.focused=focused;
        }
    }
    static Info choose(List<Info> windows, String pkg) {
        Info selected=null;
        for (Info candidate : windows) {
            if (candidate == null || candidate.id < 0 || pkg == null || !pkg.equals(candidate.pkg)) continue;
            if (selected == null || candidate.layer > selected.layer
                    || (candidate.layer == selected.layer && candidate.active && !selected.active)
                    || (candidate.layer == selected.layer && candidate.active == selected.active && candidate.focused && !selected.focused))
                selected=candidate;
        }
        return selected;
    }
    static boolean recoverOwned(int expectedDisplay,int actualDisplay,int taskId,boolean owned,String requestedPackage,String taskPackage){
        return expectedDisplay>0&&actualDisplay==expectedDisplay&&taskId>=0&&owned&&requestedPackage!=null&&requestedPackage.equals(taskPackage);
    }
    static boolean backTargets(int selectedId, String selectedPackage, int activeId, String activePackage) {
        return selectedId >= 0 && selectedId == activeId && selectedPackage != null && selectedPackage.equals(activePackage);
    }
}
