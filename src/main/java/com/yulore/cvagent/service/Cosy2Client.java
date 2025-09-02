package com.yulore.cvagent.service;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

// REF: https://github.com/OpenFeign/feign-form
@FeignClient(
        name = "cosy2Client",
        url = "${cosy2.url}",
        configuration = Cosy2Client.MultipartSupportConfig.class
)
public interface Cosy2Client {

    @PostMapping(value = "/inference_zero_shot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<byte[]> inferenceZeroShot(
            @RequestPart("tts_text") String ttsText,
            @RequestPart("prompt_text") String promptText,
            @RequestPart("prompt_wav") MultipartFile promptWav
    );

    class MultipartSupportConfig {

        @Autowired
        private ObjectFactory<HttpMessageConverters> messageConverters;

        @Bean
        public Encoder feignFormEncoder () {
            return new SpringFormEncoder(new SpringEncoder(messageConverters));
        }
    }
}