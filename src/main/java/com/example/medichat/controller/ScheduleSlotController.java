package com.example.medichat.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.medichat.service.ScheduleSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schedule")
public class ScheduleSlotController {
    @Autowired
    private ScheduleSlotService scheduleSlotService;
    /**
     * 加号接口
     * @param doctorId 医生ID
     * @param date     日期，格式 2026-06-17
     * @param period   时段 AM/PM
     * @param count    申请加的数量
     */
    @PostMapping("/addSlot")
    @SentinelResource(value = "schedule.addSlot",blockHandler = "addSlotBlock")
    public String addSlot(@RequestParam Long doctorId,
                          @RequestParam String date,
                          @RequestParam String period,
                          @RequestParam Integer count){
        scheduleSlotService.addSlot(doctorId, date, period, count);
        return "加号成功";
    }
    // 降级方法（超过限流阈值时触发）
    public String addSlotBlock(Long doctorId, String date, String period,
                               Integer count, BlockException e) {
        return "操作频繁，请稍后重试";
    }
}

