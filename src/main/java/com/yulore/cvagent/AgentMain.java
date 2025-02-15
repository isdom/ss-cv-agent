package com.yulore.cvagent;


import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;
import com.yulore.cvagent.service.LocalCosyVoiceService;
import io.netty.util.concurrent.DefaultThreadFactory;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AgentMain {
    @PostConstruct
    public void start() throws InterruptedException {
        log.info("CosyVoice-Agent: started with redisson: {}", redisson.getConfig().useSingleServer().getDatabase());

        scheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("reportExecutor"));

        final long beginTimestamp = System.currentTimeMillis();
        while (!localCosyVoiceService.isCosyVoiceOnline()) {
            log.warn("local CosyVoice !NOT! Online, wait for re-try");
            Thread.sleep(1000 * 10);
        }
        log.info("agent({}): wait for CosyVoice Service Online cost: {} s",
                agentId, (System.currentTimeMillis() - beginTimestamp) / 1000.0f);

        final RRemoteService rs = redisson.getRemoteService(_service_cosyvoice);
        rs.register(CosyVoiceService.class, localCosyVoiceService);

        final CVMasterService masterService = redisson.getRemoteService(_service_master)
                .get(CVMasterService.class, RemoteInvocationOptions.defaults().noAck().noResult());

        localCosyVoiceService.setInferenceZeroShotHook(
                // start to work
                () -> masterService.updateCVAgentStatus(agentId, 0),
                // worker back to idle
                () -> masterService.updateCVAgentStatus(agentId, 1));
        reportAndScheduleNext(()->masterService.updateCVAgentStatus(agentId, rs.getFreeWorkers(CosyVoiceService.class)));
    }

    private void reportAndScheduleNext(final Runnable doReport) {
        try {
            doReport.run();
        } finally {
            scheduler.schedule(()->reportAndScheduleNext(doReport), _report_check_interval, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
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
    private LocalCosyVoiceService localCosyVoiceService;

    @Value("${agent.report_interval:10000}") // default: 10 * 1000ms
    private long _report_check_interval;

    private ScheduledExecutorService scheduler;

    private final String agentId = UUID.randomUUID().toString();
}