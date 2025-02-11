package com.yulore.cvagent.service;

import com.aliyun.oss.OSS;
import com.google.common.primitives.Bytes;
import com.yulore.bst.BuildStreamTask;
import com.yulore.bst.OSSStreamTask;
import com.yulore.util.ByteArrayListInputStream;
import com.yulore.util.ExceptionUtil;
import com.yulore.util.WaveUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class CosyVoiceServiceImpl implements CosyVoiceService {
    @Autowired
    public CosyVoiceServiceImpl(final OSS ossClient) {
        this._ossClient = ossClient;
    }

    private Runnable onInferenceZeroShot = null;

    public void setOnInferenceZeroShot(final Runnable runnable) {
        onInferenceZeroShot = runnable;
    }

    @Override
    public String inferenceZeroShotAndSave(final String ttsText,
                                           final String promptText,
                                           final String promptWav,
                                           final String bucket,
                                           final String saveTo) {
        if (null != onInferenceZeroShot) {
            onInferenceZeroShot.run();
        }
        log.info("inferenceZeroShotAndSave: \ntext:{}\nprompt:{}-{}\nsaveTo:{bucket={}}{}", ttsText, promptText, promptWav, bucket, saveTo);
        final byte[] wavBytes = inferenceZeroShot(ttsText, promptText, promptWav);
        if (wavBytes == null) {
            log.info("inferenceZeroShotAndSave: inferenceZeroShot failed");
            return "failed";
        } else {
            log.info("inferenceZeroShotAndSave: inferenceZeroShot with output size: {}", wavBytes.length);
            _ossClient.putObject(bucket, saveTo, new ByteArrayInputStream(wavBytes));
            log.info("inferenceZeroShotAndSave: saveTo {bucket={}}{}", bucket, saveTo);
            return "OK";
        }
    }

    @Override
    public byte[] inferenceZeroShot(final String ttsText, final String promptText, final String promptWav) {
        final byte[] promptWavBytes = loadFromOss(promptWav);
        try(final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpPost httpPost = new HttpPost(_cosy2_url);

            // 构建 MultipartEntity
            final HttpEntity entity = MultipartEntityBuilder.create()
                    .setCharset(StandardCharsets.UTF_8) // 设置全局字符编码为 UTF-8
                    .addTextBody("tts_text", ttsText, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
                    .addTextBody("prompt_text", promptText, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
                    .addBinaryBody("prompt_wav", promptWavBytes, ContentType.APPLICATION_OCTET_STREAM, "prompt_wav")
                    .build();

            // 设置请求体
            httpPost.setEntity(entity);

            // 发送请求并获取响应
            HttpResponse response = httpClient.execute(httpPost);

            // 检查响应状态码
            if (response.getStatusLine().getStatusCode() == 200) {
                // 保存响应内容到文件
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    final byte[] pcm = EntityUtils.toByteArray(responseEntity);
                    log.info("callCosy2ZeroShot: Response {}/audioData.size: {}", response, pcm.length);
                    return Bytes.concat(
                            WaveUtil.genWaveHeader(16000, 1),
                            WaveUtil.resamplePCM(pcm, 24000, 16000));
                }
            } else {
                log.warn("Failed to get response. Status code: {}", response.getStatusLine().getStatusCode());
            }
        } catch (IOException ex) {
            log.warn("callCosy2ZeroShot: failed, detail: {}", ExceptionUtil.exception2detail(ex));
        }
        return null;
    }

    private byte[] loadFromOss(final String objectWithBucket) {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<byte[]> bytesRef = new AtomicReference<>(null);

        final BuildStreamTask bst = new OSSStreamTask(objectWithBucket, _ossClient, false);
        final List<byte[]> byteList = new ArrayList<>();
        bst.buildStream(byteList::add, (ignored) -> {
            try (final InputStream is = new ByteArrayListInputStream(byteList);
                 final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                is.transferTo(bos);
                bytesRef.set(bos.toByteArray());
                cdl.countDown();
            } catch (IOException ignored1) {
            }
        });
        try {
            cdl.await();
        } catch (final InterruptedException ex) {
            log.warn("loadFromOss failed: {}", ExceptionUtil.exception2detail(ex));
        }
        return bytesRef.get();
    }

    @Value("${cosy2.url}")
    private String _cosy2_url;

    private final OSS _ossClient;
}
