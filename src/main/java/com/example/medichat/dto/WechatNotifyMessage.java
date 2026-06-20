package com.example.medichat.dto;

import lombok.Data;

@Data
public class WechatNotifyMessage {
    private Long appointmentId;
    private Long patientId;
    private String content;  // 通知内容
}