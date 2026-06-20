package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long doctorId;
    private LocalDate slotDate;
    private String period;        // AM / PM
    private Integer queueNo;      // 取号后才赋值，预约阶段为null
    private String status;        // CONFIRMED/CHECKED_IN/IN_CONSULTATION/COMPLETED/CANCELLED/NO_SHOW
    private String cancelType;    // PATIENT_CANCEL/DOCTOR_CANCEL
    private LocalDateTime checkInTime;
    private LocalDateTime callTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
