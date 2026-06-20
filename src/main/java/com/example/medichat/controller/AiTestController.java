package com.example.medichat.controller;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTestController {

    @Autowired
    private ChatModel chatModel;

    @GetMapping("/ai/test")
    public String test(@RequestParam String message) {
        return chatModel.chat(message);
    }
}