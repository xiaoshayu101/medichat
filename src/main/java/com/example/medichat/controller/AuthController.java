package com.example.medichat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证权限模块 - Controller层
 * 基于 Sa-Token 实现登录、登出、权限校验等功能
 * 支持医生端、患者端、管理端三种角色的身份认证
 * 本模块由团队其他成员负责实现
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    // TODO: 登录接口 POST /auth/login
    // TODO: 登出接口 POST /auth/logout
    // TODO: 刷新Token接口 POST /auth/refresh
    // TODO: 获取当前用户信息 GET /auth/userInfo
}