package com.yulore.cvagent;


import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;
import com.yulore.cvagent.service.LocalCosyVoiceService;
import com.yulore.util.ExceptionUtil;
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
import java.util.function.Function;

@Slf4j
@Component
public class AgentMain {
    @PostConstruct
    public void start() throws InterruptedException {
        log.info("CosyVoice-Agent: started with redisson: {}", redisson.getConfig().useSingleServer().getDatabase());

        scheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("reportExecutor"));

        checkAndScheduleNext((tm) -> {
            if (!localCosyVoiceService.isCosyVoiceOnline()) {
                log.warn("agent({}) local_cosyvoice_not_online, wait for re-try", agentId);
                // continue to check CosyVoice status
                return true;
            } else {
                log.info("agent({}): wait_for_local_cosyvoice_online_cost: {} s",
                        agentId, (System.currentTimeMillis() - tm) / 1000.0f);

                registerCosyVoiceServiceAndStartUpdateAgentStatus();
                // stop checking CosyVoice status for service is online
                return false;
            }
        }, System.currentTimeMillis());
    }

    private void registerCosyVoiceServiceAndStartUpdateAgentStatus() {
        // CosyVoice is online, stop check and begin to register RemoteService
        final RRemoteService rs = redisson.getRemoteService(_service_cosyvoice);
        rs.register(CosyVoiceService.class, localCosyVoiceService);

        final CVMasterService masterService = redisson.getRemoteService(_service_master)
                .get(CVMasterService.class, RemoteInvocationOptions.defaults().noAck().noResult());

        localCosyVoiceService.setInferenceZeroShotHook(
                // start to work
                () -> masterService.updateCVAgentStatus(agentId, 0),
                // worker back to idle
                () -> masterService.updateCVAgentStatus(agentId, 1));

        checkAndScheduleNext((startedTimestamp)-> {
            masterService.updateCVAgentStatus(agentId, rs.getFreeWorkers(CosyVoiceService.class));
            return true;
        }, System.currentTimeMillis());
    }

    private void checkAndScheduleNext(final Function<Long, Boolean> doCheck, final Long timestamp) {
        try {
            if (doCheck.apply(timestamp)) {
                scheduler.schedule(()->checkAndScheduleNext(doCheck, timestamp), _check_interval, TimeUnit.MILLISECONDS);
            }
        } catch (final Exception ex) {
            log.warn("agent({}) checkAndScheduleNext exception: {}", agentId, ExceptionUtil.exception2detail(ex));
        }
        finally {
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

    @Value("${agent.check_interval:10000}") // default: 10 * 1000ms
    private long _check_interval;

    private ScheduledExecutorService scheduler;

    private final String agentId = UUID.randomUUID().toString();
}