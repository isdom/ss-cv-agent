package com.yulore.cvagent;


import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Component
public class AgentMain {

    @PostConstruct
    public void start() {
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        log.info("CosyVoice-Agent: shutdown");
    }
}