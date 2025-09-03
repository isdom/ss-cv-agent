package com.yulore.api;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Builder
@Data
@ToString
public class ZeroShotTask {
    public String task_id;    // "<uuid>"
    public String tts_text;   // text
    public String prompt_text; // text
    public String prompt_wav;  // {bucket=bucket_name1}yyyy.wav
    public String bucket;      // bucket_name2
    public String save_to;     // object_name
}
