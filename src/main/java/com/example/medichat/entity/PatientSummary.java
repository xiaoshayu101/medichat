package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("patient_summary")
public class PatientSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long patientId;

    private String summary;

    private String esDocId;

    private LocalDateTime createTime;
}