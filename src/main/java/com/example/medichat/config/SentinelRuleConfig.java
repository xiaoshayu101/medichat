package com.example.medichat.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        DegradeRule rule = new DegradeRule();

        // 针对哪个资源（跟@SentinelResource里的value一致）
        rule.setResource("ai.consult.chat");

        // 熔断策略：慢调用比例
        rule.setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType());

        // 慢调用阈值：响应时间超过5000ms算慢调用
        rule.setCount(5000);

        // 触发熔断的慢调用比例阈值：50%
        rule.setSlowRatioThreshold(0.5);

        // 熔断持续时间：30秒
        rule.setTimeWindow(30);

        // 统计窗口内最少请求数：至少5次请求才开始计算比例
        rule.setMinRequestAmount(5);

        // 统计时间窗口：10秒内的请求
        rule.setStatIntervalMs(10000);

        rules.add(rule);
        DegradeRuleManager.loadRules(rules);

        System.out.println("Sentinel慢调用熔断规则已加载：ai.consult.chat");
    }
}