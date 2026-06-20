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
    @SentinelResource(value = "schedule.addSlot",blockHandler = "addSlotBlock")//当该方法触发了限流（比如并发量过大）或熔断时，程序不会直接抛出异常导致系统崩溃，而是会跳转到当前类中名为 addSlotBlock 的方法去执行。
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
//有了Sentinel注解后：
//正常情况：方法正常执行，加号逻辑运行。
//高并发情况：Sentinel 拦截到请求超过阈值，直接调用 addSlotBlock 方法，返回给前端一个“系统繁忙，请稍后再试”的提示，从而保护了核心系统的稳定性。

}

