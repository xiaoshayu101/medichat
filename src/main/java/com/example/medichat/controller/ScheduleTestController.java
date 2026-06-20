package com.example.medichat.controller;

import com.example.medichat.entity.ScheduleRule;
import com.example.medichat.mapper.ScheduleRuleMapper;
import com.example.medichat.service.ScheduleRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScheduleTestController {
    @Autowired
    private ScheduleRuleService scheduleRuleService;
    @GetMapping("/test/rules")
    public List<ScheduleRule> listRules(){
        return scheduleRuleService.listAllRules();
    }

}
