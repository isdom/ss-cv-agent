package com.yulore.cvagent.service;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

// REF: https://github.com/OpenFeign/feign-form
@FeignClient(
        name = "cosy2Client",
        url = "${cosy2.url}",
        configuration = Cosy2Client.MultipartSupportConfig.class
)
public interface Cosy2Client {

    // @PostMapping(value = "/inference_zero_shot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // byte[] parameter
    // @RequestLine("POST /inference_zero_shot")
    //@RequestMapping(
    //        value = "/inference_zero_shot",
    //        method = RequestMethod.POST)
    // @Headers("Content-Type: multipart/form-data")
    @PostMapping(value = "/inference_zero_shot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<byte[]> inferenceZeroShot(
            @RequestPart("tts_text") String ttsText,
            @RequestPart("prompt_text") String promptText,
            @RequestPart("prompt_wav") MultipartFile promptWav
            //@Param("tts_text") String ttsText,
            //@Param("prompt_text") String promptText,
            //@Param("prompt_wav") byte[] promptWav
    );

    public class MultipartSupportConfig {

        @Autowired
        private ObjectFactory<HttpMessageConverters> messageConverters;

        @Bean
        public Encoder feignFormEncoder () {
            return new SpringFormEncoder(new SpringEncoder(messageConverters));
        }
    }
}