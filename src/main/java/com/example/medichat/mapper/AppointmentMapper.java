package com.example.medichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medichat.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
    @Select("SELECT MAX(queue_no) FROM appointment " +
            "WHERE doctor_id = #{doctorId} AND slot_date = #{slotDate} AND period = #{period}")
    Integer selectMaxQueueNo(@Param("doctorId") Long doctorId,
                             @Param("slotDate") LocalDate slotDate,
                             @Param("period") String period);
    @Select("SELECT MAX(queue_no) FROM appointment " +
            "WHERE doctor_id = #{doctorId} AND slot_date = #{slotDate} AND period = #{period} " +
            "AND status IN ('IN_CONSULTATION', 'COMPLETED')")
    Integer selectMaxCalledQueueNo(@Param("doctorId") Long doctorId,
                                   @Param("slotDate") LocalDate slotDate,
                                   @Param("period") String period);
}
