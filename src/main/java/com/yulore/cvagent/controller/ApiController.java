package com.yulore.cvagent.controller;

import com.aliyun.oss.OSS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @Autowired
    public ApiController(final OSS ossClient) {
        this._ossClient = ossClient;
    }

    @RequestMapping(value = "/zero_shot", method = RequestMethod.GET)
    @ResponseBody
    public Object zero_shot() {
        log.info("API call /zero_shot with OSS: {}", _ossClient);
        return "OK";
    }

    private final OSS _ossClient;
}
