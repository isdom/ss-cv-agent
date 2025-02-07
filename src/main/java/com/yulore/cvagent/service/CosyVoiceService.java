package com.yulore.cvagent.service;

public interface CosyVoiceService {
    byte[] inferenceZeroShot(final String ttsText, final String promptText, final byte[] wavBytes);
}
