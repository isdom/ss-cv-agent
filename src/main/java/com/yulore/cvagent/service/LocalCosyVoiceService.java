package com.yulore.cvagent.service;

import com.yulore.api.CosyVoiceService;

public interface LocalCosyVoiceService extends CosyVoiceService {

    void setInferenceZeroShotHook(final Runnable onStart, final Runnable onEnd);
    byte[] inferenceZeroShot(final String ttsText, final String promptText, final String promptWav);
}
