package com.example.medichat.service;

/**
 * 预约信用分模块 - Service接口
 * 负责患者预约行为的信用分管理：
 *   - 患者主动取消（PATIENT_CANCEL）：扣10分
 *   - 医生取消（DOCTOR_CANCEL）：补偿患者5分
 *   - 爽约（NO_SHOW）：扣20分
 *   - 正常完成就诊（COMPLETE）：加2分
 * 信用分低于60分时限制预约次数
 * 本模块由团队其他成员负责实现，本人参与提出DOCTOR_CANCEL与PATIENT_CANCEL区分的设计思路
 */
public interface CreditService {

    // TODO: 处理预约取消事件，根据取消类型计算扣/加分
    // void handleCancelEvent(Long appointmentId, String cancelType);

    // TODO: 处理爽约事件（XXL-Job定时扫描未签到的预约触发）
    // void handleNoShowEvent(Long appointmentId);

    // TODO: 查询患者当前信用分
    // Integer getScore(Long patientId);

    // TODO: 校验患者是否有预约资格（信用分是否达标）
    // boolean checkEligibility(Long patientId);
}