package com.yulore.cvagent;


import com.yulore.cvagent.service.CosyVoiceService;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Component
public class AgentMain {

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private CosyVoiceService cosyVoiceService;

    @PostConstruct
    public void start() {
        log.info("CosyVoice-Agent: Init: redisson: {}", redisson.getConfig().useSingleServer().getDatabase());

        final RRemoteService remoteService = redisson.getRemoteService(_service_zero_shot);
        remoteService.register(CosyVoiceService.class, cosyVoiceService);
    }

    @PreDestroy
    public void stop() {
        final RRemoteService remoteService = redisson.getRemoteService(_service_zero_shot);
        remoteService.deregister(CosyVoiceService.class);

        log.info("CosyVoice-Agent: shutdown");
    }

    @Value("${service.zero_shot}")
    private String _service_zero_shot;
}