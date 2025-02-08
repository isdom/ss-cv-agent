package com.yulore.cvagent.service;

public interface CosyVoiceService {
    String inferenceZeroShot(final String ttsText, final String promptText, final String promptWav);
}
