package com.yulore.cvagent.controller;

import com.aliyun.oss.OSS;
import com.google.common.primitives.Bytes;
import com.yulore.bst.BuildStreamTask;
import com.yulore.bst.OSSStreamTask;
import com.yulore.util.ByteArrayListInputStream;
import com.yulore.util.ExceptionUtil;
import com.yulore.util.WaveUtil;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @Data
    @ToString
    static class ZeroShotRequest {
        private String tts_text;
        private String prompt_text;
        private String prompt_wav;
    }

    @Autowired
    public ApiController(final OSS ossClient) {
        this._ossClient = ossClient;
    }

    @RequestMapping(value = "/zero_shot", method = RequestMethod.POST)
    public void zero_shot(@RequestBody ZeroShotRequest request, final HttpServletResponse response) throws InterruptedException {
        log.info("API call /zero_shot with OSS: {}", _ossClient);

        // final CountDownLatch cdl = new CountDownLatch(1);

        // final AtomicReference<byte[]> wavBytesRef = new AtomicReference<>(null);

        final BuildStreamTask bst = new OSSStreamTask(request.getPrompt_wav(), _ossClient, false);
        final List<byte[]> byteList = new ArrayList<>();
        bst.buildStream(byteList::add, (ignored) -> {
            try (final InputStream is = new ByteArrayListInputStream(byteList);
                 final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                is.transferTo(bos);
                final byte[] promptWavBytes = bos.toByteArray();
                log.info("zero_shot: ttsText:{} / promptText:{} / promptWav size:{}", request.getTts_text(), request.getPrompt_text(), promptWavBytes.length);
                final byte[] wavBytes = callCosy2ZeroShot(request.getTts_text(), request.getPrompt_text(), promptWavBytes);
                // cdl.countDown();
                if (wavBytes != null) {
                    log.info("zero_shot: output wav size:{}", wavBytes.length);

                    // 设置响应头
                    response.setContentType("audio/wav");
                    response.setHeader("Content-Disposition", "attachment; filename=\"output.wav\"");
                    response.setContentLength(wavBytes.length);

                    // 写入响应体
                    try (final ByteArrayInputStream bis = new ByteArrayInputStream(wavBytes)) {
                        bis.transferTo(response.getOutputStream());
                    }
                } else {
                    log.info("zero_shot: output wav failed");
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to generate WAV file");
                }
            } catch (IOException ignored1) {
            }
        });

//        cdl.await();
//        if (wavBytesRef.get() != null) {
//            log.info("zero_shot: output wav size:{}", wavBytesRef.get().length);
//            return wavBytesRef.get();
//        } else {
//            log.info("zero_shot: output wav failed");
//            return "failed";
//        }
    }

    private byte[] callCosy2ZeroShot(final String ttsText, final String promptText, final byte[] wavBytes) {
        try {
            // 创建 HttpClient 实例
            final HttpClient httpClient = HttpClients.createDefault();
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
                            resamplePCM(pcm, 24000, 16000));
                }
            } else {
                log.warn("Failed to get response. Status code: {}", response.getStatusLine().getStatusCode());
            }
        } catch (IOException ex) {
            log.warn("callCosy2ZeroShot: failed, detail: {}", ExceptionUtil.exception2detail(ex));
        }
        return null;
    }

    // 转换采样率
    private static byte[] resamplePCM(final byte[] pcm, final int sourceSampleRate, final int targetSampleRate) {
        final AudioFormat sf = new AudioFormat(sourceSampleRate, 16, 1, true, false);
        final AudioFormat tf = new AudioFormat(targetSampleRate, 16, 1, true, false);
        try(// 创建原始音频格式
            final AudioInputStream ss = new AudioInputStream(new ByteArrayInputStream(pcm), sf, pcm.length);
            // 创建目标音频格式
            final AudioInputStream ts = AudioSystem.getAudioInputStream(tf, ss);
            // 读取转换后的数据
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
        ) {
            ts.transferTo(os);
            return os.toByteArray();
        } catch (Exception ex) {
            log.warn("resamplePCM: failed, detail: {}", ExceptionUtil.exception2detail(ex));
            return null;
        }
    }

    @Value("${cosy2.url}")
    private String _cosy2_url;

    private final OSS _ossClient;
}
