package com.yulore.cvagent;


import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;
import com.yulore.cvagent.service.LocalCosyVoiceServiceImpl;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;

@Slf4j
@Component
public class AgentMain {
    @PostConstruct
    public void start() {
        log.info("CosyVoice-Agent: started with redisson: {}", redisson.getConfig().useSingleServer().getDatabase());

        final RRemoteService remoteService =  redisson.getRemoteService(_service_cosyvoice);
        // redisson.getRemoteService(_service_cosyvoice)
        remoteService.register(CosyVoiceService.class, localCosyVoiceService);

        final CVMasterService masterService =  redisson.getRemoteService(_service_master)
                .get(CVMasterService.class, RemoteInvocationOptions.defaults().noAck().noResult());

        localCosyVoiceService.setInferenceZeroShotHook(
                // start to work
                () -> masterService.updateCVAgentStatus(agentId, 0),
                // worker back to idle
                () -> masterService.updateCVAgentStatus(agentId, 1));
        masterService.updateCVAgentStatus(agentId, 1);

        log.info("remoteService.getFreeWorkers() for CosyVoiceService: {}", remoteService.getFreeWorkers(CosyVoiceService.class));
    }

    @PreDestroy
    public void stop() {
        final RRemoteService remoteService = redisson.getRemoteService(_service_cosyvoice);
        remoteService.deregister(CosyVoiceService.class);

        log.info("CosyVoice-Agent: shutdown");
    }

    @Value("${service.cosyvoice}")
    private String _service_cosyvoice;

    @Value("${service.cv_master}")
    private String _service_master;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private LocalCosyVoiceServiceImpl localCosyVoiceService;

    private final String agentId = UUID.randomUUID().toString();
}