package com.example.medichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;

@Data
@TableName("schedule_rule")
public class ScheduleRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private String ruleType;   // WEEKLY / BIWEEKLY
    private String weekday;    // JSON数组字符串，如 "[1,3,5]"
    private String period;     // AM / PM / ALL_DAY
    private Integer slotCount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;



}
