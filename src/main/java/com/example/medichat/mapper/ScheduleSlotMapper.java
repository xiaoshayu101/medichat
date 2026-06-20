package com.example.medichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medichat.entity.ScheduleRule;
import com.example.medichat.entity.ScheduleSlot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleSlotMapper extends BaseMapper<ScheduleSlot> {

}
