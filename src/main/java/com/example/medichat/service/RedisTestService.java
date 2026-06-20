package com.example.medichat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

    @Service//
    public class RedisTestService{
        @Autowired
        private StringRedisTemplate stringRedisTemplate;

        public void save(String key,String value){
            stringRedisTemplate.opsForValue().set(key,value);
        }
        public String get(String key){
            return stringRedisTemplate.opsForValue().get(key);
        }
    }
