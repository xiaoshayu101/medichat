package com.example.medichat.service;

public interface ScheduleSlotService {
    /**
     * 为未来7天生成号源
     */
    void generateFutureSlots();

    void addSlot(Long doctorId, String date, String period, Integer count);
}

