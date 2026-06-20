package com.example.medichat.controller;

import com.example.medichat.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class AppointmentController {
    @Autowired
    private AppointmentService appointmentService;

    @PostMapping("/appointment/checkIn")
    public String checkIn(@RequestParam Long appointmentId){
        Integer queueNo = appointmentService.checkIn(appointmentId);
        return "取号成功，您的排队序号是：" + queueNo;
    }
    @PostMapping("/appointment/callNext")
    public String callNext(@RequestParam Long doctorId,
                           @RequestParam String date,
                           @RequestParam String period) {
        Long appointmentId = appointmentService.callNext(doctorId, date, period);
        if (appointmentId == null) {
            return "当前队列为空，没有患者在排队";
        }
        return "叫号成功，appointmentId=" + appointmentId;
    }
    @PostMapping("/appointment/complete")
    public String complete(@RequestParam Long appointmentId) {
        appointmentService.completeConsultation(appointmentId);
        return "就诊结束";
    }

}
