package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("schedule_slot")
public class ScheduleSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private LocalDate slotDate;
    private String period;     // AM / PM
    private Integer totalCount;
    private Integer remainCount;
    private Integer isGenerated;//是否已同步到Redis
    private LocalDateTime createTime;
    private Integer status; // 1=正常 0=已失效
}
