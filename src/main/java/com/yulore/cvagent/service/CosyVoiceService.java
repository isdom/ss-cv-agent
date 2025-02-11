package com.yulore.cvagent.service;

public interface CosyVoiceService {
    byte[] inferenceZeroShot(final String ttsText, final String promptText, final String promptWav);
    String inferenceZeroShotAndSave(final String ttsText, final String promptText, final String promptWav,
                                      final String bucket, final String saveTo);

    void setOnInferenceZeroShot(final Runnable runnable);
}
