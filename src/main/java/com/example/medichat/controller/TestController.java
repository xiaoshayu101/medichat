package com.example.medichat.controller;
import com.example.medichat.service.RedisTestService;
import com.example.medichat.service.ScheduleSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    private RedisTestService redisTestService;

    @GetMapping("/set")
    public String set(@RequestParam String key,
                      @RequestParam String value) {
        redisTestService.save(key, value);
        return "写入成功";
    }

    @GetMapping("/get")
    public String get(@RequestParam String key) {
        return redisTestService.get(key);
    }

    @Autowired
    private ScheduleSlotService scheduleSlotService;

    @GetMapping("/generate-slots")
    public String generateSlots() {
        scheduleSlotService.generateFutureSlots();
        return "号源生成完成";
    }

}