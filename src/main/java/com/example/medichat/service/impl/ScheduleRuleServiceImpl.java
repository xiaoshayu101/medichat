package com.example.medichat.service.impl;

import com.example.medichat.entity.ScheduleRule;
import com.example.medichat.mapper.ScheduleRuleMapper;
import com.example.medichat.service.ScheduleRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleRuleServiceImpl implements ScheduleRuleService {
    @Autowired
    private ScheduleRuleMapper scheduleRuleMapper;
    @Override
    public List<ScheduleRule> listAllRules(){
        return scheduleRuleMapper.selectList(null);
    }
}
