package com.example.medichat.service;

public interface AppointmentService {
    /**
     * 取号
     * @param appointmentId 预约记录ID
     * @return 排队序号
     */
    Integer checkIn(Long appointmentId);
    Long callNext(Long doctorId, String date, String period);
    /**
     * 医生点击结束就诊
     */
    void completeConsultation(Long appointmentId);

    /**
     * 动态推送候诊等待时间
     */
    void pushWaitingEstimate();
}
