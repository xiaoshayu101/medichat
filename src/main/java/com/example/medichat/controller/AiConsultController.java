package com.example.medichat.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.medichat.service.AiConsultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/consult")
public class AiConsultController {

    @Autowired
    private AiConsultService aiConsultService;

    /**
     * 预问诊对话接口
     */
    @PostMapping("/chat")
    @SentinelResource(value = "ai.consult.chat", blockHandler = "chatBlock")
    public String chat(@RequestParam Long patientId,
                       @RequestParam String sessionId,
                       @RequestParam String userMessage) {
        return aiConsultService.chat(patientId, sessionId, userMessage);
    }
    /**
     * 熔断/限流时的降级方法
     * 参数列表必须跟原方法一致，最后加一个 BlockException 参数
     */
    public String chatBlock(Long patientId, String sessionId,
                            String userMessage, BlockException e) {
        return "AI问诊服务繁忙，请稍后重试";
    }


    /**
     * 结束问诊，生成摘要存入长期记忆
     */
    @PostMapping("/finish")
    public String finish(@RequestParam Long patientId,
                         @RequestParam String sessionId) {
        aiConsultService.finishConsult(patientId, sessionId);
        return "问诊结束，摘要已存入长期记忆";
    }
}