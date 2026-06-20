package com.example.medichat.config;

import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 权限认证配置
 * 配置路由拦截规则、Token有效期、多端登录策略等
 * 本模块由团队其他成员负责实现
 */
@Configuration
public class SaTokenConfig {

    // TODO: 配置Sa-Token拦截器，放行公开接口（如登录、取号），拦截需要认证的接口
    // TODO: 配置Token有效期（建议7天）和自动续签策略
    // TODO: 配置多端登录互斥策略（医生端和患者端使用不同的StpUtil实例）
}