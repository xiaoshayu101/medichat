package com.example.medichat.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultationDurationMessage {
    private Long doctorId;
    private LocalDateTime callTime;   // 叫号时间（就诊开始）
    private LocalDateTime endTime;    // 就诊结束时间
    private Integer weekday;          // 星期几，1-7
    private String period;            // AM/PM
}