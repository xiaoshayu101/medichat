package com.example.medichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medichat.entity.CreditRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约信用分记录 Mapper
 * 本模块由团队其他成员负责实现
 */
@Mapper
public interface CreditRecordMapper extends BaseMapper<CreditRecord> {

    // TODO: 查询某患者当前信用总分
    // Integer selectTotalScoreByPatientId(Long patientId);
}