package com.github.paicoding.forum.service.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 操作工具类
 */
@Component
public class RedisUtil {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Redis 增加操作
     *
     * @param key 键
     * @param value 增加的值
     */
    public void incr(String key, long value) {
        redisTemplate.opsForValue().increment(key, value);
    }

    /**
     * Redis 减少操作
     *
     * @param key 键
     * @param value 减少的值
     */
    public void decr(String key, long value) {
        redisTemplate.opsForValue().decrement(key, value);
    }

    /**
     * 设置 Redis key 的值
     *
     * @param key 键
     * @param value 值
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取 Redis key 的值
     *
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 根据需要，添加更多 Redis 操作的方法，如删除、获取 hash 等
}

