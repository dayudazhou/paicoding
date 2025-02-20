//package com.github.paicoding.forum.service.article.repository.listener;
//
//import java.util.Optional;
//
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//import com.alibaba.fastjson.JSONObject;
//import com.github.paicoding.forum.api.model.constant.KafkaTopicConstant;
//import com.github.paicoding.forum.api.model.dto.ArticleKafkaMessageDTO;
//import com.github.paicoding.forum.service.constant.RedisConstant;
//import com.github.paicoding.forum.service.utils.RedisUtil;
//
//import lombok.extern.slf4j.Slf4j;
//
///**
// * kafka监听文章数据
// *
// * @ClassName: ArticleKafkaListener
// * @Author: ygl
// * @Date: 2023/6/15 14:26
// * @Version: 1.0
// */
//@Component
//@Slf4j
//public class ArticleKafkaListener {
//
//    @Autowired
//    private RedisUtil redisUtil;
//
//    private final String totalPre = RedisConstant.REDIS_PAI + RedisConstant.REDIS_PRE_ARTICLE
//            + RedisConstant.TOTAL;
//    private final String praisePre = RedisConstant.REDIS_PAI + RedisConstant.REDIS_PRE_ARTICLE
//            + RedisConstant.PRAISE;
//    private final String collectionPre = RedisConstant.REDIS_PAI + RedisConstant.REDIS_PRE_ARTICLE
//            + RedisConstant.COLLECTION;
//    private final String commentPre = RedisConstant.REDIS_PAI + RedisConstant.REDIS_PRE_ARTICLE
//            + RedisConstant.COMMENT;
//    private final String recoverPre = RedisConstant.REDIS_PAI + RedisConstant.REDIS_PRE_ARTICLE
//            + RedisConstant.RECOVER;
//
//    /**
//     * 监听主题为paicoding_article的消息
//     * @param consumerRecord
//     */
//    @KafkaListener(topics = {KafkaTopicConstant.ARTICLE_TOPIC})
//    public void consumer(ConsumerRecord<?, ?> consumerRecord) {
//
//        log.info("监听中、、、");
//        Optional<?> value = Optional.ofNullable(consumerRecord.value());
//
//        if (value.isPresent()) {
//            String msg = value.get().toString();
//            String msgStr = JSONObject.toJSONString(msg);
//            ArticleKafkaMessageDTO articleKafkaMessageDTO = JSONObject.parseObject(msg, ArticleKafkaMessageDTO.class);
//            int type = articleKafkaMessageDTO.getType();
//            Long userId = articleKafkaMessageDTO.getTargetUserId();
//
//            // 下面是业务逻辑处理
//            // 2-点赞、4-取消点赞；3-收藏、5-取消点赞；
//            if (type == 2) {
//                redisUtil.incr(praisePre + userId, 1);
//                redisUtil.incr(totalPre + userId, 1);
//            } else if (type == 4) {
//                redisUtil.decr(praisePre + userId, 1);
//                redisUtil.decr(totalPre + userId, 1);
//            } else if (type == 3) {
//                redisUtil.incr(collectionPre + userId, 1);
//                redisUtil.incr(totalPre + userId, 1);
//            } else if (type == 5) {
//                redisUtil.decr(collectionPre + userId, 1);
//                redisUtil.decr(totalPre + userId, 1);
//            } else if (type == 6) {
//                redisUtil.incr(commentPre + userId, 1);
//                redisUtil.incr(totalPre + userId, 1);
//            } else if (type == 8) {
//                redisUtil.incr(recoverPre + userId, 1);
//                redisUtil.incr(totalPre + userId, 1);
//            }
//            log.info("消费消息：{}", msgStr);
//        }
//    }
//
//}
//
