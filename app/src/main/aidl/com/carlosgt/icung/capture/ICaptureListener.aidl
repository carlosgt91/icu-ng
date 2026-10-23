// See ICaptureService.aidl.
package com.carlosgt.icung.capture;

oneway interface ICaptureListener {
    /**
     * @param state IDLE, CONNECTING, STREAMING or ERROR
     * @param message user-facing text, may be null
     * @param camera 0 back, 1 front, 2 USB
     */
    void onStatus(String state, String message, int camera);
}
