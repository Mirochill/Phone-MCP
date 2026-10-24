package fr.phonemcp;
import android.os.IBinder;
import fr.phonemcp.IPreviewCallback;
import fr.phonemcp.IParallelGestureDispatcher;
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
    String physicalScreenState(boolean on, boolean userPresent) = 9;
    void setGestureDispatcher(IParallelGestureDispatcher dispatcher, String epoch) = 10;
    void accessibilitySettingsChanged() = 11;
    String setPhysicalScreenAwake(boolean keepOn, long revision) = 12;
    String savePreviewImage(String arguments, in android.os.ParcelFileDescriptor destination) = 13;
    String attachRecording(String arguments, in android.view.Surface destination) = 14;
    String renewRecording(String arguments) = 15;
    void detachRecording(String recordingId) = 16;
    void destroy() = 16777114;
}

