package com.example.medichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medichat.entity.PatientSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PatientSummaryMapper extends BaseMapper<PatientSummary> {
    @Select("SELECT patient_id, COUNT(*) as cnt FROM patient_summary " +
            "GROUP BY patient_id HAVING cnt > 20")
    List<Map<String, Object>> selectPatientsWithTooManySummaries();
}

