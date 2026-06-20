package com.example.medichat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约信用分模块 - Controller层
 * 提供信用分查询、扣分记录查询等对外接口
 * 本模块由团队其他成员负责实现
 */
@RestController
@RequestMapping("/credit")
public class CreditController {

    // TODO: GET /credit/score?patientId=xxx 查询患者当前信用分
    // TODO: GET /credit/records?patientId=xxx 查询信用分变动明细
}