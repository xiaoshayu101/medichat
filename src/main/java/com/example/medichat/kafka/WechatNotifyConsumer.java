package com.example.medichat.kafka;

import com.example.medichat.dto.WechatNotifyMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WechatNotifyConsumer {

    @KafkaListener(topics = "medichat.notify.wechat", groupId = "medichat-notify-group")
    public void handleNotify(WechatNotifyMessage message) {
        // 真实场景这里要调用微信模板消息接口，但这里用的打日志模拟这个调用
        sendWechatTemplateMessage(message);
    }

    /**
     * 模拟调用微信模板消息接口
     * 真实环境需要替换成真正的HTTP调用，传入微信的AppID/AppSecret
     */
    private void sendWechatTemplateMessage(WechatNotifyMessage message) {
        System.out.println("===== 模拟发送微信通知 =====");
        System.out.println("患者ID: " + message.getPatientId());
        System.out.println("预约ID: " + message.getAppointmentId());
        System.out.println("通知内容: " + message.getContent());
        System.out.println("============================");
    }
}