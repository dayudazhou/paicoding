package com.github.paicoding.forum.core.dto;

import lombok.Data;
/**
 * 文章 Kafka 消息传输对象
 */
@Data
public class ArticleKafkaMessageDTO {

    /**
     * 类型（例如：点赞：2，取消点赞：4，收藏：3，取消收藏：5，评论：6，恢复：8）
     */
    private int type;

    /**
     * 操作用户的用户名
     */
    private String sourceUserName;

    /**
     * 目标用户的 ID
     */
    private Long targetUserId;

    /**
     * 文章标题
     */
    private String articleTitle;

    /**
     * 操作类型的描述（例如：点赞、评论等）
     */
    private String typeName;

    // 你可以根据需要添加更多字段
}
