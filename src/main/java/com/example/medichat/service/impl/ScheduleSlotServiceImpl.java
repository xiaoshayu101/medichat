package com.example.medichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.medichat.entity.ScheduleException;
import com.example.medichat.entity.ScheduleRule;
import com.example.medichat.entity.ScheduleSlot;
import com.example.medichat.mapper.ScheduleExceptionMapper;
import com.example.medichat.mapper.ScheduleRuleMapper;
import com.example.medichat.mapper.ScheduleSlotMapper;
import com.example.medichat.service.ScheduleSlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class ScheduleSlotServiceImpl implements ScheduleSlotService {
    @Autowired
    private ScheduleRuleMapper scheduleRuleMapper;
    @Autowired
    private ScheduleExceptionMapper scheduleExceptionMapper;
    @Autowired
    private ScheduleSlotMapper scheduleSlotMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @XxlJob("generateFutureSlotsJob")
    @Override
    public void generateFutureSlots(){
//        1.查看所有排班规则
        List<ScheduleRule> allRules = scheduleRuleMapper.selectList(null);
//       2.遍历未来7天
        LocalDate today = LocalDate.now();
        for (int i = 0;i < 7;i++){
            LocalDate targetDate = today.plusDays(i);
//            3.对每个医生的每条规则，判断这一天是否出诊
            for (ScheduleRule rule : allRules){
                processRuleForDate(rule,targetDate);
            }
        }
    }
    @Override
    public void addSlot(Long doctorId, String date, String period, Integer count) {
        // 1. 查MySQL里这个时段的号源记录，拿到原始total_count
        ScheduleSlot slot = scheduleSlotMapper.selectOne(
                new QueryWrapper<ScheduleSlot>()
                        .eq("doctor_id", doctorId)
                        .eq("slot_date", date)
                        .eq("period", period)
                        .eq("status", 1)
        );

        if (slot == null) {
            throw new RuntimeException("该时段号源不存在或已失效");
        }

        // 2. 计算允许的最大总数（原始total × 1.2，向下取整）
        int maxAllowed = (int) (slot.getTotalCount() * 1.2);

        // 3. 构建Redis key
        String redisKey = "slot:" + doctorId + ":" + date + ":" + period;

        // 4. 执行Lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/add_slot.lua")
        ));

        Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(count),
                String.valueOf(maxAllowed)
        );

        // 5. 判断Lua返回值
        if (result == null || result == -2L) {
            throw new RuntimeException("Redis中号源数据不存在，请稍后重试");
        }
        if (result == -1L) {
            throw new RuntimeException("加号数量超过上限（不超过原始号源的20%）");
        }

        // 6. Lua执行成功，更新MySQL的total_count
        slot.setTotalCount(slot.getTotalCount() + count);
        scheduleSlotMapper.updateById(slot);

        // 7. 插入例外记录留审计
        ScheduleException exception = new ScheduleException();
        exception.setDoctorId(doctorId);
        exception.setExceptionDate(LocalDate.parse(date));
        exception.setType("ADD_SLOT");
        exception.setPeriod(period);
        exception.setExtraCount(count);
        exception.setReason("医生加号操作");
        scheduleExceptionMapper.insert(exception);
    }
    private void processRuleForDate(ScheduleRule rule,LocalDate targetDate){
//        1.检查这一天是否在规则有效期内
        if (targetDate.isBefore(rule.getValidFrom())){
            return;// 规则还没生效
        }
        if (rule.getValidTo() != null && targetDate.isAfter(rule.getValidTo())){
            return;// 规则还没生效
        }
//        2.检查这一天的星期是否在规则的weekday里
        int weekday = targetDate.getDayOfWeek().getValue();// 周一=1 ... 周日=7
        if (!isWeekdayMatched(rule.getWeekday(),weekday)){
            return;// 这一天不在规则规定的出诊星期里
        }
//        3.检查是否有例外记录覆盖这一天
        List<ScheduleException> exceptions = findExceptions(rule.getDoctorId(), targetDate);
        // 从列表里分别取出三种类型（每种最多一条，因为有唯一约束）
        ScheduleException cancelException = findByType(exceptions, "CANCEL");
        ScheduleException halfDayException = findByType(exceptions, "HALF_DAY");
        ScheduleException addSlotException = findByType(exceptions, "ADD_SLOT");

        if (cancelException != null){
            return;// 这天被取消出诊了，不生成号源
        }
//        4.确定"出诊时段集合"
        List<String> basePeriods = resolveBasePeriods(rule,halfDayException);
//        5.对每个基础时段，生成号源（按需叠加加号）
        for (String period : basePeriods){
            int extraCount = 0;
            if (addSlotException != null && period.equals(addSlotException.getPeriod())){
                extraCount = addSlotException.getExtraCount() != null ? addSlotException.getExtraCount() : 0;
            }
            createOrUpdateSlot(rule.getDoctorId(),targetDate,period,rule.getSlotCount(),extraCount);
        }
    }

    /**
     * 确定这一天的"基础出诊时段集合"
     * - 没有HALF_DAY例外：按规则的period展开（ALL_DAY拆成AM+PM）
     * - 有HALF_DAY例外：只保留例外指定的那个period
     */
    private List<String> resolveBasePeriods(ScheduleRule rule, ScheduleException halfDayException) {
        List<String> periods = new ArrayList<>();
        if ("ALL_DAY".equals(rule.getPeriod())){
            periods.add("AM");
            periods.add("PM");
        }else {
            periods.add(rule.getPeriod());// AM 或 PM
        }
        // HALF_DAY例外：只保留例外指定的period
        if (halfDayException !=null && "HALF_DAY".equals(halfDayException.getType())){
            String halfDayPeriod = halfDayException.getPeriod();
                periods = new ArrayList<>();
                periods.add(halfDayPeriod);
            }
        return periods;
    }

    /**
     * 创建或更新号源记录
     */
    private void createOrUpdateSlot(Long doctorId, LocalDate date, String period, int baseCount, int extraCount) {
        int totalCount = baseCount + extraCount;
//        先查这条记录是否已存在
        ScheduleSlot existing = scheduleSlotMapper.selectOne(
                new QueryWrapper<ScheduleSlot>()
                        .eq("doctor_id", doctorId)
                        .eq("slot_date", date)
                        .eq("period", period)
        );
        if (existing == null) {
//            不存在，新建
            ScheduleSlot slot = new ScheduleSlot();
            slot.setDoctorId(doctorId);
            slot.setSlotDate(date);
            slot.setPeriod(period);
            slot.setTotalCount(totalCount);
            slot.setRemainCount(totalCount);
            slot.setIsGenerated(0);
            scheduleSlotMapper.insert(slot);
        }else {
//            已存在，需要判断 totalCount 是否需要调整
            if (existing.getTotalCount() != totalCount){
//                说明出现了新的例外（比如加号），total发生了变化
                int diff = totalCount - existing.getTotalCount();
                existing.setTotalCount(totalCount);
                existing.setRemainCount(existing.getRemainCount()+diff);
                scheduleSlotMapper.updateById(existing);
            }
        }
//
    }

    private ScheduleException findByType(List<ScheduleException> exceptions, String type) {
        for (ScheduleException e : exceptions) {
            if (type.equals(e.getType())) {
                return e;
            }
        }
        return null;
    }

    private List<ScheduleException> findExceptions(Long doctorId, LocalDate date) {
        return scheduleExceptionMapper.selectList(
                new QueryWrapper<ScheduleException>()
                        .eq("doctor_id",doctorId)
                        .eq("exception_date",date)
        );
    }

    /**
     * 判断目标星期数是否在规则的weekday数组里
     * weekday存的是JSON字符串，如 "[1,3,5]"
     */
    private boolean isWeekdayMatched(String weekdayJson, int targetweekday) {
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            int[] weekdays = objectMapper.readValue(weekdayJson, int[].class);
            for (int w :weekdays){
                if (w == targetweekday){
                    return true;
                }
            }
        }catch (Exception e){
            e.printStackTrace();//把错误信息打印到控制台日志里，方便后续排查"是哪个医生哪条规则的数据出了问题"。
        }
        return false;
    }

    // 调整出诊接口的核心逻辑
    public void adjustSchedule(Long doctorId, LocalDate date, String type, String period,Integer extraCount,String reason, Long operatorId) {
        // 先查这一天这个type的例外记录是否已存在
        ScheduleException existing = scheduleExceptionMapper.selectOne(
                new QueryWrapper<ScheduleException>()
                        .eq("doctor_id", doctorId)
                        .eq("exception_date", date)
                        .eq("type", type)
        );

        if (existing == null) {
            // 不存在，新增
            ScheduleException newException = new ScheduleException();
            scheduleExceptionMapper.insert(newException);
        } else {
            // 已存在，更新
            existing.setPeriod(period);
            existing.setExtraCount(extraCount);
            existing.setReason(reason);
            existing.setOperatorId(operatorId);
            scheduleExceptionMapper.updateById(existing);
        }
    }

}
