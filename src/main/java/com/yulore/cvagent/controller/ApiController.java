package com.yulore.cvagent.controller;

import com.aliyun.oss.OSS;
import com.yulore.bst.BuildStreamTask;
import com.yulore.bst.OSSStreamTask;
import com.yulore.cvagent.service.CosyVoiceService;
import com.yulore.util.ByteArrayListInputStream;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @Data
    @ToString
    static public class ZeroShotRequest {
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

        final BuildStreamTask bst = new OSSStreamTask(request.getPrompt_wav(), _ossClient, false);
        final List<byte[]> byteList = new ArrayList<>();
        bst.buildStream(byteList::add, (ignored) -> {
            try (final InputStream is = new ByteArrayListInputStream(byteList);
                 final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                is.transferTo(bos);
                final byte[] promptWavBytes = bos.toByteArray();
                log.info("zero_shot: ttsText:{} / promptText:{} / promptWav size:{}", request.getTts_text(), request.getPrompt_text(), promptWavBytes.length);
                final byte[] wavBytes = cosyService.inferenceZeroShot(request.getTts_text(), request.getPrompt_text(), promptWavBytes);
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
    }

    private final OSS _ossClient;

    @Autowired
    CosyVoiceService cosyService;
}
