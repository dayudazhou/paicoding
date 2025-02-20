package com.github.paicoding.forum.service.notify.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.rabbitmq.RabbitmqConnection;
import com.github.paicoding.forum.core.rabbitmq.RabbitmqConnectionPool;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.repository.entity.FollowEventData;
import com.github.paicoding.forum.service.notify.service.NotifyService;
import com.github.paicoding.forum.service.notify.service.RabbitmqService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RabbitmqServiceImpl implements RabbitmqService {

    @Autowired
    @Lazy
    private NotifyService notifyService;

    @Override
    public boolean enabled() {
        return "true".equalsIgnoreCase(SpringUtil.getConfig("rabbitmq.switchFlag"));
    }

    @Autowired
    private ApplicationEventPublisher publisher;


    private NotifyMsgListener notifyMsgListener;

    @Override
    public void publishMsg(String exchange,
                           BuiltinExchangeType exchangeType,
                           String toutingKey,
                           String message) {
        try {
            //创建连接
            RabbitmqConnection rabbitmqConnection = RabbitmqConnectionPool.getConnection();
            Connection connection = rabbitmqConnection.getConnection();
            //创建消息通道
            Channel channel = connection.createChannel();
            // 声明exchange中的消息为可持久化，不自动删除
            channel.exchangeDeclare(exchange, exchangeType, true, false, null);
            // 发布消息
            channel.basicPublish(exchange, toutingKey, null, message.getBytes());
            log.info("Publish msg: {}", message);
            channel.close();
            RabbitmqConnectionPool.returnConnection(rabbitmqConnection);
        } catch (InterruptedException | IOException | TimeoutException e) {
            log.error("rabbitMq消息发送异常: exchange: {}, msg: {}", exchange, message, e);
        }

    }

    @Override
    public void consumerMsg(String exchange,
                            String queueName,
                            String routingKey) {

        try {
            //创建连接
            RabbitmqConnection rabbitmqConnection = RabbitmqConnectionPool.getConnection();
            Connection connection = rabbitmqConnection.getConnection();
            //创建消息信道
            final Channel channel = connection.createChannel();
            //声明交换机，确保交换机存在
            channel.exchangeDeclare(exchange,BuiltinExchangeType.DIRECT, true, false, null);
            //消息队列
            channel.queueDeclare(queueName, true, false, false, null);
            //绑定队列到交换机
            channel.queueBind(queueName, exchange, routingKey);

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                                           byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");
                    log.info("Consumer msg: {}", message);


//                    NotifyMsgEvent<UserFootDO> event = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<UserFootDO>>() {});
                    NotifyMsgEvent<JsonNode> event = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<JsonNode>>() {});
                    NotifyTypeEnum notifyType = event.getNotifyType();


                    // 获取Rabbitmq消息，并保存到DB
                    // 说明：这里仅作为示例，如果有多种类型的消息，可以根据消息判定，简单的用 if...else 处理，复杂的用工厂 + 策略模式
/*                    if(notifyType == NotifyTypeEnum.PRAISE){
//                        notifyService.saveArticleNotify(JsonUtil.toObj(message, UserFootDO.class), NotifyTypeEnum.PRAISE);

                        notifyService.saveArticleNotify(userFoot, NotifyTypeEnum.PRAISE);


                    }*/
                    if(notifyType == NotifyTypeEnum.COLLECT
                    || notifyType == NotifyTypeEnum.PRAISE){
                        NotifyMsgEvent<UserFootDO> event2 = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<UserFootDO>>() {});
                        publisher.publishEvent(event2);
                        //notifyService.saveArticleNotify(event2);
                    }
                    else if(notifyType == NotifyTypeEnum.CANCEL_PRAISE || notifyType == NotifyTypeEnum.CANCEL_COLLECT){
                        NotifyMsgEvent<UserFootDO> event2 = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<UserFootDO>>() {});
                        //notifyService.removeArticleNotify(event2);
                        publisher.publishEvent(event2);
                    }
                    else if(notifyType == NotifyTypeEnum.COMMENT){
                        NotifyMsgEvent<CommentDO> event1 = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<CommentDO>>() {});
                      //  notifyService.saveCommentNotify(event1);
                        publisher.publishEvent(event1);
                    }
                    else if(notifyType == NotifyTypeEnum.REPLY){
                        NotifyMsgEvent<CommentDO> event1 = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<CommentDO>>() {});
                       // notifyService.saveReplyNotify(event1);
                        publisher.publishEvent(event1);
                    }
                    else if(notifyType == NotifyTypeEnum.FOLLOW
                    || notifyType == NotifyTypeEnum.CANCEL_FOLLOW){
                        NotifyMsgEvent<FollowEventData> event1 = JsonUtil.toObj(message, new TypeReference<NotifyMsgEvent<FollowEventData>>() {});
                        publisher.publishEvent(event1);
                    }


                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            };
            // 取消自动ack
            channel.basicConsume(queueName, false, consumer);

            Thread.sleep(1000);

            // 关闭 channel 和连接
            if (channel.isOpen()) {
                channel.close();
            }

            RabbitmqConnectionPool.returnConnection(rabbitmqConnection);
        } catch (InterruptedException | IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processConsumerMsg() {
        log.info("Begin to processConsumerMsg.");

        Integer stepTotal = 1;
        Integer step = 0;

        // TODO: 这种方式非常 Low，后续会改造成阻塞 I/O 模式
        while (true) {
            step++;
            try {
                log.info("processConsumerMsg cycle.");
                consumerMsg(CommonConstants.EXCHANGE_NAME_DIRECT, CommonConstants.QUERE_NAME_PRAISE,
                        CommonConstants.QUERE_KEY_PRAISE);
                if (step.equals(stepTotal)) {
                    Thread.sleep(1000);
                    step = 0;
                }
            } catch (Exception e) {

            }
        }
    }
}
