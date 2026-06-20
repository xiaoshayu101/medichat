package com.example.medichat.service.impl;

import com.example.medichat.service.DurationStatService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DurationStatServiceImpl implements DurationStatService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @XxlJob("updateDurationModelJob")
    public void updateDurationModel() {
        // 找出所有 duration:raw:* 的key
        Set<String> keys = scanKeys("duration:raw:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            // key格式：duration:raw:{doctorId}:{weekday}:{period}
            String[] parts = key.split(":");
            String doctorId = parts[2];
            String weekday = parts[3];
            String period = parts[4];

            List<String> rawData = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (rawData == null || rawData.isEmpty()) {
                continue;
            }

            // 计算本周新数据的平均值
            double newAvg = rawData.stream()
                    .mapToLong(Long::parseLong)
                    .average()
                    .orElse(0);
//            如果列表里没有数据（或者计算结果为空），它会返回 0 作为默认值，确保程序永远不会崩溃

            // 读取历史模型值
            String modelKey = "duration:model:" + doctorId;
            String hashField = weekday + ":" + period;
            String historyValueStr = (String) stringRedisTemplate.opsForHash().get(modelKey, hashField);

            double finalValue;
            if (historyValueStr == null) {
                // 没有历史数据，直接用本周数据
                finalValue = newAvg;
            } else {
                // 加权平均：新数据权重0.3，历史权重0.7
                double historyValue = Double.parseDouble(historyValueStr);
                finalValue = newAvg * 0.3 + historyValue * 0.7;
            }

            // 写入模型表
            stringRedisTemplate.opsForHash().put(modelKey, hashField, String.valueOf(finalValue));

            // 清空本周原始数据，准备下一周重新累积
            stringRedisTemplate.delete(key);
        }
    }
    /**
     * 用scan命令游标式扫描匹配的key，替代keys命令
     * 避免一次性扫描整个Redis实例阻塞其他业务（本项目同一Redis实例上还跑着排班、叫号队列等其他业务的key）
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> result = new HashSet<>();
        stringRedisTemplate.execute((RedisConnection connection) -> {
            Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build()
            );
            while (cursor.hasNext()) {
                result.add(new String(cursor.next()));
            }
            return null;
        }, true);
        return result;
    }
}