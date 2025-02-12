package com.yulore.cvagent.service;

import com.yulore.api.CosyVoiceService;
import org.redisson.Redisson;
import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.config.Config;

public class CosyVoiceServiceDemo {
    public static void main(final String[] args) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("url")
                .setPassword("passwd")
                .setDatabase(0);

        // 创建 RedissonClient 实例
        final RedissonClient redisson = Redisson.create(config);

        final RRemoteService remoteService = redisson.getRemoteService("service");
        final CosyVoiceService cosyVoiceService = remoteService.get(CosyVoiceService.class,
                RemoteInvocationOptions.defaults().noAck().expectResultWithin(30 * 1000L));

        final String result = cosyVoiceService.inferenceZeroShotAndSave(
                "您好吧？",
                "参考音频",
                "ref.wav",
                "bucket",
                "saveto");
        redisson.shutdown();
    }
}
