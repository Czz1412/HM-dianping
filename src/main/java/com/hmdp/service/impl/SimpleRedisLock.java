package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ISimpleRedisLock;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ISimpleRedisLock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true);

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeOutSec) {
        // 1.获取线程标识
        long id = Thread.currentThread().getId();
        String value = VALUE_PREFIX + id;

        // 2.获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, value, timeOutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unlock() {
        // 先获取锁中的数据
        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        // 判断是否与自己的数据相等
        long id = Thread.currentThread().getId();
        String value = VALUE_PREFIX + id;
        if (s.equals(value)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
