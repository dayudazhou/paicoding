package com.github.paicoding.forum.service.constant;

/**
 * Redis 常量类
 * 用于定义 Redis 中的常量前缀等
 */
public class RedisConstant {

    // Redis key 前缀
    public static final String REDIS_PAI = "forum:";  // 可以根据需要修改为合适的前缀

    // Redis key 具体内容的前缀
    public static final String REDIS_PRE_ARTICLE = "article:";

    // Redis 中不同类型的计数前缀
    public static final String TOTAL = "total";        // 总计数
    public static final String PRAISE = "praise";      // 点赞数
    public static final String COLLECTION = "collection"; // 收藏数
    public static final String COMMENT = "comment";    // 评论数
    public static final String RECOVER = "recover";    // 恢复数

    // 如果需要更多的 Redis 键值前缀，可以继续添加
    // public static final String USER = "user";
}

