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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Slf4j
@Component
public class AgentMain {
    @PostConstruct
    public void start() {
        log.info("CosyVoice-Agent: started with redisson: {}, cosy2 works: {}",
                redisson.getConfig().useSingleServer().getDatabase(), _cosyvoice_works);

        scheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("reportExecutor"));
        cosyExecutor = Executors.newFixedThreadPool(_cosyvoice_works, new DefaultThreadFactory("cosyExecutor"));
        remoteService = redisson.getRemoteService(_service_cosyvoice);

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
        final CVMasterService masterService = redisson.getRemoteService(_service_master)
                .get(CVMasterService.class, RemoteInvocationOptions.defaults().noAck().noResult());

        localCosyVoiceService.setAgentId(agentId);
        localCosyVoiceService.setMaster(masterService);
        final var currentWorks = new AtomicInteger(0);
        localCosyVoiceService.setInferenceZeroShotHook(
                // start to work
                () -> {
                    int freeWorks;
                    rsLock.lock();
                    try {
                        freeWorks = _cosyvoice_works - currentWorks.incrementAndGet();
                        if (freeWorks <= 0) {
                            log.info("freeWorks: {}, disableCosy2Facade", freeWorks);
                            disableCosy2Facade();
                        }
                    } finally {
                        rsLock.unlock();
                    }
                    masterService.updateCVAgentStatus(agentId, freeWorks);
                },
                // worker back to idle
                () -> {
                    int freeWorks;
                    rsLock.lock();
                    try {
                        freeWorks = _cosyvoice_works - currentWorks.decrementAndGet();
                        if (freeWorks == _cosyvoice_works) {
                            log.info("freeWorks: {}, enableCosy2Facade", freeWorks);
                            enableCosy2Facade();
                        }
                    } finally {
                        rsLock.unlock();
                    }
                    masterService.updateCVAgentStatus(agentId, freeWorks);
                });

        // CosyVoice is online, stop check and begin to register RemoteService
        enableCosy2Facade();

        localCosyVoiceService.beginFeedback();

        checkAndScheduleNext((startedTimestamp)-> {
            masterService.updateCVAgentStatus(agentId, /*rs.getFreeWorkers(CosyVoiceService.class)*/ _cosyvoice_works - currentWorks.get());
            return true;
        }, System.currentTimeMillis());
    }

    void enableCosy2Facade() {
        remoteService.register(CosyVoiceService.class, localCosyVoiceService, _cosyvoice_works, cosyExecutor);
    }

    void disableCosy2Facade() {
        remoteService.deregister(CosyVoiceService.class);
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
        disableCosy2Facade();
        cosyExecutor.shutdown();

        log.info("CosyVoice-Agent: shutdown");
    }

    @Value("${service.cosyvoice}")
    private String _service_cosyvoice;

    @Value("${service.cv_master}")
    private String _service_master;

    @Autowired
    private RedissonClient redisson;

    private RRemoteService remoteService;

    private final Lock rsLock = new ReentrantLock();

    @Autowired
    private LocalCosyVoiceService localCosyVoiceService;

    @Value("${agent.check_interval:10000}") // default: 10 * 1000ms
    private long _check_interval;

    private ScheduledExecutorService scheduler;

    @Value("${cosy2.works:1}")
    private int _cosyvoice_works;
    private ExecutorService cosyExecutor;

    private final String agentId = UUID.randomUUID().toString();
}