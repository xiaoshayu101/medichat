package com.example.medichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.medichat.dto.ConsultationDurationMessage;
import com.example.medichat.dto.WechatNotifyMessage;
import com.example.medichat.entity.Appointment;
import com.example.medichat.entity.PatientSummary;
import com.example.medichat.mapper.AppointmentMapper;
import com.example.medichat.mapper.PatientSummaryMapper;
import com.example.medichat.service.AiConsultService;
import com.example.medichat.service.AppointmentService;
import com.example.medichat.websocket.QueueWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentServiceImpl implements AppointmentService {
    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AiConsultService aiConsultService;

    @Autowired
    private PatientSummaryMapper patientSummaryMapper;

    @Override
    public Integer checkIn(Long appointmentId){
//        1.查处这条预约记录
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null){
            throw new RuntimeException("预约记录不存在");
        }
        if (!"CONFIRMED".equals(appointment.getStatus())){
            throw new RuntimeException("该预约当前状态不可取号，状态为：" + appointment.getStatus());
        }
//        2.计算下一个排队序号
        Integer maxQueueNo = appointmentMapper.selectMaxQueueNo(
                appointment.getDoctorId(),
                appointment.getSlotDate(),
                appointment.getPeriod()
        );
        int nextQueueNo = (maxQueueNo == null ? 0 : maxQueueNo)+1;
//        3.更新MySQL
        appointment.setStatus("CHECKED_IN");
        appointment.setQueueNo(nextQueueNo);
        appointment.setCheckInTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);
//        4.写入Redis Zset
        String redisKey = "queue:" + appointment.getDoctorId() + ":" + appointment.getSlotDate() + ":" + appointment.getPeriod();
        stringRedisTemplate.opsForZSet().add(redisKey, String.valueOf(appointmentId), nextQueueNo);
        return nextQueueNo;
    }
    @Override
    public Long callNext(Long doctorId, String date, String period) {
        String redisKey = "queue:" + doctorId + ":" + date + ":" + period;

        // 1.取出队列里score最小的那个（排最前面的患者）
        Set<ZSetOperations.TypedTuple<String>> result =
                stringRedisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);

        if (result == null || result.isEmpty()) {
            return null; // 队列为空，没有人在排队
        }

        ZSetOperations.TypedTuple<String> tuple = result.iterator().next();
        String appointmentIdStr = tuple.getValue();
        Long appointmentId = Long.valueOf(appointmentIdStr);

        // 2.更新MySQL状态
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            // 异常情况：Redis里有，但MySQL里查不到，先移除这个脏数据
            stringRedisTemplate.opsForZSet().remove(redisKey, appointmentIdStr);
            return null;
        }
        appointment.setStatus("IN_CONSULTATION");
        appointment.setCallTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        // 3.从Redis队列移除
        stringRedisTemplate.opsForZSet().remove(redisKey, appointmentIdStr);

        // 4.组装消息，广播给大屏
        String screenMessage = String.format(
                "{\"type\":\"CALL\",\"queueNo\":%d,\"appointmentId\":%d}",
                appointment.getQueueNo(), appointmentId
        );
        QueueWebSocketServer.sendToScreen(String.valueOf(doctorId), screenMessage);
        // 生成本次预问诊摘要，推送给医生工作台
        try {
            // sessionId用appointmentId，跟预问诊时保持一致
            String sessionId = String.valueOf(appointmentId);

            // 从短期记忆生成结构化摘要
            aiConsultService.finishConsult(appointment.getPatientId(), sessionId);

            // 查出刚生成的摘要（最新一条）
            PatientSummary latestSummary = patientSummaryMapper.selectOne(
                    new QueryWrapper<PatientSummary>()
                            .eq("patient_id", appointment.getPatientId())
                            .orderByDesc("create_time")
                            .last("LIMIT 1")
            );

            if (latestSummary != null) {
                String doctorMessage = String.format(
                        "{\"type\":\"PATIENT_SUMMARY\",\"appointmentId\":%d,\"patientId\":%d,\"summary\":\"%s\"}",
                        appointmentId,
                        appointment.getPatientId(),
                        latestSummary.getSummary().replace("\"", "\\\"").replace("\n", "\\n")
                );
                // 推送给医生端（医生工作台也通过WebSocket连接，role=doctor，id=doctorId）
                QueueWebSocketServer.sendToDoctor(String.valueOf(doctorId), doctorMessage);
            }
        } catch (Exception e) {
            // 摘要推送失败不影响叫号主流程
            System.out.println("摘要推送失败，appointmentId=" + appointmentId + "，原因：" + e.getMessage());
        }


        // 5.：推送给患者本人，判断是否在线
        String patientMessage = String.format(
                "{\"type\":\"YOUR_TURN\",\"message\":\"请前往诊室就诊\"}"
        );
        boolean sent = QueueWebSocketServer.sendToPatient(String.valueOf(appointmentId), patientMessage);

        if (!sent) {
            // WebSocket不可达，降级走微信通知
            WechatNotifyMessage notifyMessage = new WechatNotifyMessage();
            notifyMessage.setAppointmentId(appointmentId);
            notifyMessage.setPatientId(appointment.getPatientId());
            notifyMessage.setContent("请前往诊室就诊");
            kafkaTemplate.send("medichat.notify.wechat", notifyMessage);
        }

        return appointmentId;
    }
    @Override
    public void completeConsultation(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new RuntimeException("预约记录不存在");
        }
        if (!"IN_CONSULTATION".equals(appointment.getStatus())) {
            throw new RuntimeException("当前状态不是就诊中，无法结束，当前状态：" + appointment.getStatus());
        }

        LocalDateTime endTime = LocalDateTime.now();
        appointment.setStatus("COMPLETED");
        appointment.setEndTime(endTime);
        appointmentMapper.updateById(appointment);

        // 发Kafka消息，记录这次就诊时长（异步，不影响主流程）
        ConsultationDurationMessage durationMessage = new ConsultationDurationMessage();
        durationMessage.setDoctorId(appointment.getDoctorId());
        durationMessage.setCallTime(appointment.getCallTime());
        durationMessage.setEndTime(endTime);
        durationMessage.setWeekday(appointment.getSlotDate().getDayOfWeek().getValue());
        durationMessage.setPeriod(appointment.getPeriod());

        kafkaTemplate.send("medichat.consultation.duration", durationMessage);
    }

    @Override
    @Scheduled(fixedRate = 120000) // 每120000毫秒=2分钟执行一次
    public void pushWaitingEstimate() {
        // 1.找出所有"今天有候诊患者"的医生+时段组合
        // 这里简化处理：直接查所有今天status=CHECKED_IN的记录，按医生+时段分组
        LocalDate today = LocalDate.now();
        List<Appointment> waitingList = appointmentMapper.selectList(
                new QueryWrapper<Appointment>()
                        .eq("slot_date", today)
                        .eq("status", "CHECKED_IN")
        );

        if (waitingList.isEmpty()) {
            return; // 没有候诊患者，不用推送
        }

        // 按 doctorId+period 分组处理
        waitingList.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getDoctorId() + ":" + a.getPeriod()
                ))
                .forEach((key, patients) -> {
                    String[] parts = key.split(":");
                    Long doctorId = Long.valueOf(parts[0]);
                    String period = parts[1];
                    processWaitingGroup(doctorId, period, today, patients);
                });
    }

    /**
     * 处理某个医生某个时段的候诊队列，计算并推送等待时间
     */
    private void processWaitingGroup(Long doctorId, String period, LocalDate date, List<Appointment> patients) {
        // 2.找出当前叫号指针（已经叫到的最大queue_no）
        // 查这个医生这一天这个时段，状态是IN_CONSULTATION或COMPLETED里queue_no最大的
        Integer currentPointer = appointmentMapper.selectMaxCalledQueueNo(doctorId, date, period);
        int pointer = currentPointer == null ? 0 : currentPointer;

        // 第三步：读取duration模型
        int weekday = date.getDayOfWeek().getValue();
        String modelKey = "duration:model:" + doctorId;
        String hashField = weekday + ":" + period;
        String avgDurationStr = (String) stringRedisTemplate.opsForHash().get(modelKey, hashField);

        // 没有模型数据时，用一个默认估算值（比如10分钟=600秒）兜底
        double avgDurationSeconds = avgDurationStr != null ? Double.parseDouble(avgDurationStr) : 600;

        // 第四步：对每个候诊患者计算等待时间并推送
        for (Appointment patient : patients) {
            int peopleAhead = patient.getQueueNo() - pointer - 1;
            if (peopleAhead < 0) {
                peopleAhead = 0;
            }
            long estimatedSeconds = (long) (peopleAhead * avgDurationSeconds);
            long estimatedMinutes = estimatedSeconds / 60;

            String message = String.format(
                    "{\"type\":\"WAITING_UPDATE\",\"peopleAhead\":%d,\"estimatedMinutes\":%d}",
                    peopleAhead, estimatedMinutes
            );
            boolean sent = QueueWebSocketServer.sendToPatient(String.valueOf(patient.getId()), message);
            System.out.println("推送候诊患者id=" + patient.getId() + "，推送结果：" + sent + "，消息内容：" + message);
        }
    }


}
