package com.yulore.api;

public interface CosyVoiceService {
    String inferenceZeroShotAndSave(final String ttsText,
                                    final String promptText,
                                    final String promptWav,
                                    final String bucket,
                                    final String saveTo);

    /**
     * @param task : ZeroShotTask
     * @return agentId
     */
    void commitZeroShotTask(final ZeroShotTask task);
}
