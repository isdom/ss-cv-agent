package com.yulore.cvagent.controller;

import com.yulore.cvagent.service.CosyVoiceService;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

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

    @RequestMapping(value = "/zero_shot", method = RequestMethod.POST)
    public void zero_shot(@RequestBody ZeroShotRequest request, final HttpServletResponse response) throws IOException {
        log.info("zero_shot: ttsText:{} / promptText:{} / promptWav:{}", request.tts_text, request.prompt_text, request.prompt_wav);
        final String wavBytesBase64 = cosyService.inferenceZeroShot(request.tts_text, request.prompt_text, request.prompt_wav);

        if (!Strings.isEmpty(wavBytesBase64)) {
            final byte[] wavBytes = Base64.getDecoder().decode(wavBytesBase64);
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
    }

    @Autowired
    CosyVoiceService cosyService;
}
