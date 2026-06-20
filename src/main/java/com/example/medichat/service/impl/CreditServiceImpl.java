package com.example.medichat.service.impl;

import com.example.medichat.service.CreditService;
import org.springframework.stereotype.Service;

/**
 * 预约信用分模块 - Service实现层
 * 本模块由团队其他成员负责实现
 * 本人参与协作点：提出区分PATIENT_CANCEL和DOCTOR_CANCEL两种取消类型的设计思路
 * （医生主动取消不应扣患者信用分，反而应该给予补偿，以维护医患公平性）
 */
@Service
public class CreditServiceImpl implements CreditService {

    // TODO: 注入 CreditRecordMapper，AppointmentMapper
    // TODO: 实现扣分/加分逻辑，每次变动插入credit_record明细记录
    // TODO: 信用分低于60分时，在预约接口处拦截（结合Sa-Token拦截器实现）
}