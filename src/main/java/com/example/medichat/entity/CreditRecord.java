package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约信用分记录实体
 * 记录每次加分/扣分的明细，支持追溯
 * 本模块由团队其他成员负责实现
 */
@Data
@TableName("credit_record")
public class CreditRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 患者ID
    private Long patientId;

    // 关联的预约ID
    private Long appointmentId;

    // 变动类型：PATIENT_CANCEL（患者取消扣分）/ DOCTOR_CANCEL（医生取消补偿）
    // NO_SHOW（爽约扣分）/ COMPLETE（正常就诊加分）
    private String changeType;

    // 变动分值（正数为加分，负数为扣分）
    private Integer changeScore;

    // 变动后的总分
    private Integer totalScore;

    // 备注
    private String remark;

    private LocalDateTime createTime;
}