package fr.phonemcp;
import android.os.IBinder;
import fr.phonemcp.IPreviewCallback;
import android.view.SurfaceControl;
interface IBackgroundControl {
    String start(long deadlineElapsed, IBinder owner, boolean screenOff, boolean parallelDisplay) = 0;
    String execute(String operation, String arguments) = 1;
    void stop() = 2;
    String restorePanel() = 3;
    String previewFrame(String arguments) = 4;
    void setPreviewCallback(IPreviewCallback callback) = 5;
    String attachPreview(String arguments, in SurfaceControl parent) = 6;
    String renewPreview(String arguments) = 7;
    void detachPreview(String viewerId) = 8;
    void destroy() = 16777114;
}
