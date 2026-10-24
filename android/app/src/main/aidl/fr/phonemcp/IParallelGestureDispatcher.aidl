package fr.phonemcp;
import fr.phonemcp.IParallelGestureResult;
oneway interface IParallelGestureDispatcher {
    void revoke(String epoch, String sessionId, int displayId);
    void dispatch(String epoch, String sessionId, String packageName, int displayId, int transportRevision, String request, IParallelGestureResult result);
}
