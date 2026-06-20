package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("schedule_exception")
public class ScheduleException {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private LocalDate exceptionDate;
    private String type;       // ADD_SLOT / CANCEL / HALF_DAY
    private String period;     // HALF_DAY时指定AM或PM
    private Integer extraCount;
    private String reason;
    private Long operatorId;
    private LocalDateTime createTime;
}
