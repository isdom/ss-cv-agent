package com.yulore.cvagent.service;

import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;

public interface LocalCosyVoiceService extends CosyVoiceService {

    void setAgentId(final String agentId);
    void setMaster(final CVMasterService masterService);
    void beginFeedback();

    void setInferenceZeroShotHook(final Runnable onStart, final Runnable onEnd);
    byte[] inferenceZeroShot(final String ttsText, final String promptText, final String promptWav);
    boolean isCosyVoiceOnline();

}
