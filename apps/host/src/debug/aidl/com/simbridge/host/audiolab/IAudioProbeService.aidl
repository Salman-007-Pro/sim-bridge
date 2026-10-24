package com.simbridge.host.audiolab;

interface IAudioProbeService {
    void destroy() = 16777114;
    String identity() = 1;
    String probeCapture(int source, int sampleRateHz, int durationMs) = 2;
    String probeTelephonyTx(int durationMs) = 3;
}
