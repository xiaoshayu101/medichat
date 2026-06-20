package com.example.medichat.service;

import com.example.medichat.entity.ScheduleRule;

import java.util.List;

public interface ScheduleRuleService {
    /**
     * 查询所有排班规则
     */
    List<ScheduleRule> listAllRules();
}
