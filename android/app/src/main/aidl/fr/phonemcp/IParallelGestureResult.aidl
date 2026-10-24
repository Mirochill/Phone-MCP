package fr.phonemcp;
oneway interface IParallelGestureResult {
    void finished(String requestId, int displayId, boolean accepted, boolean completed, boolean uncertain, String error);
}
