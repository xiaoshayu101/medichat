package com.example.medichat.kafka;

import com.example.medichat.dto.ConsultationDurationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ConsultationDurationConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = "medichat.consultation.duration", groupId = "medichat-duration-group")
    public void handleDuration(ConsultationDurationMessage message) {
        // 计算这次实际就诊时长（秒）
        long seconds = Duration.between(message.getCallTime(), message.getEndTime()).getSeconds();

        // 存进一个临时列表，供XXL-Job每周统计时读取
        // key格式：duration:raw:{doctorId}:{weekday}:{period}
        String key = "duration:raw:" + message.getDoctorId() + ":"
                + message.getWeekday() + ":" + message.getPeriod();
        stringRedisTemplate.opsForList().rightPush(key, String.valueOf(seconds));
    }
}