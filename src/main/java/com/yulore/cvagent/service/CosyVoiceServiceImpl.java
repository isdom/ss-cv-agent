package com.yulore.cvagent.service;

import com.google.common.primitives.Bytes;
import com.yulore.util.ExceptionUtil;
import com.yulore.util.WaveUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class CosyVoiceServiceImpl implements CosyVoiceService {
    @Override
    public byte[] inferenceZeroShot(final String ttsText, final String promptText, final byte[] wavBytes) {
        try(final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpPost httpPost = new HttpPost(_cosy2_url);

            // 构建 MultipartEntity
            final HttpEntity entity = MultipartEntityBuilder.create()
                    .setCharset(StandardCharsets.UTF_8) // 设置全局字符编码为 UTF-8
                    .addTextBody("tts_text", ttsText, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
                    .addTextBody("prompt_text", promptText, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
                    .addBinaryBody("prompt_wav", wavBytes, ContentType.APPLICATION_OCTET_STREAM, "prompt_wav")
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
                    byte[] pcm = EntityUtils.toByteArray(responseEntity);
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

    @Value("${cosy2.url}")
    private String _cosy2_url;
}
