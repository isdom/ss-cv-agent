package com.yulore.api;

public interface CVMasterService {
    void updateCVAgentStatus(final String agentId, final int freeWorks);
    // status =>
    //  1: progress
    //  -1: failed
    //  0: success
    void feedbackZeroShotStatus(final String agentId, final String task_id, final int status);
}
