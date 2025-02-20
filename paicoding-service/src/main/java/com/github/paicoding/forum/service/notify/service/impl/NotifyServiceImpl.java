package com.github.paicoding.forum.service.notify.service.impl;

import com.beust.jcommander.internal.Sets;
import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.enums.DocumentTypeEnum;
import com.github.paicoding.forum.api.model.enums.NotifyStatEnum;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import com.github.paicoding.forum.core.cache.RedisClient;
import com.github.paicoding.forum.core.util.NumUtil;
import com.github.paicoding.forum.core.ws.WebSocketResponseUtil;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.comment.service.CommentReadService;
import com.github.paicoding.forum.service.notify.repository.dao.NotifyMsgDao;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyMsgDO;
import com.github.paicoding.forum.service.notify.service.NotifyService;
import com.github.paicoding.forum.service.statistics.constants.CountConstants;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.service.UserRelationService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author YiHui
 * @date 2022/9/4
 */
@Slf4j
@Service
public class NotifyServiceImpl implements NotifyService {
    @Resource
    private NotifyMsgDao notifyMsgDao;

    @Resource
    private UserRelationService userRelationService;

    private final ArticleReadService articleReadService;

    private final CommentReadService commentReadService;
    /**
     * 记录用户与对应的jwt token之间的缓存关系；用于websocket的广播通知
     */
    private LoadingCache<Long, Set<String>> wsUserSessionCache;

    public NotifyServiceImpl(ArticleReadService articleReadService, CommentReadService commentReadService) {
        this.articleReadService = articleReadService;
        this.commentReadService = commentReadService;
    }


    @PostConstruct
    public void init() {
        wsUserSessionCache = CacheBuilder.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build(new CacheLoader<Long, Set<String>>() {
                    @Override
                    public Set<String> load(Long aLong) throws Exception {
                        return new HashSet<>();
                    }
                });
    }

    @Override
    public int queryUserNotifyMsgCount(Long userId) {
        return notifyMsgDao.countByUserIdAndStat(userId, NotifyStatEnum.UNREAD.getStat());
    }

    /**
     * 查询消息通知列表
     *
     * @return
     */
    @Override
    public PageListVo<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, PageParam page) {
        List<NotifyMsgDTO> list = notifyMsgDao.listNotifyMsgByUserIdAndType(userId, type, page);
        if (CollectionUtils.isEmpty(list)) {
            return PageListVo.emptyVo();
        }

        // 设置消息为已读状态
        notifyMsgDao.updateNotifyMsgToRead(list);
        // 更新全局总的消息数
        ReqInfoContext.getReqInfo().setMsgNum(queryUserNotifyMsgCount(userId));
        // 更新当前登录用户对粉丝的关注状态
        updateFollowStatus(userId, list);
        return PageListVo.newVo(list, page.getPageSize());
    }

    private void updateFollowStatus(Long userId, List<NotifyMsgDTO> list) {
        List<Long> targetUserIds = list.stream().filter(s -> s.getType() == NotifyTypeEnum.FOLLOW.getType()).map(NotifyMsgDTO::getOperateUserId).collect(Collectors.toList());
        if (targetUserIds.isEmpty()) {
            return;
        }

        // 查询userId已经关注过的用户列表；并将对应的msg设置为true，表示已经关注过了；不需要再关注
        Set<Long> followedUserIds = userRelationService.getFollowedUserId(targetUserIds, userId);
        list.forEach(notify -> {
            if (followedUserIds.contains(notify.getOperateUserId())) {
                notify.setMsg("true");
            } else {
                notify.setMsg("false");
            }
        });
    }

    @Override
    public Map<String, Integer> queryUnreadCounts(long userId) {
        Map<Integer, Integer> map = Collections.emptyMap();
        if (ReqInfoContext.getReqInfo() != null && NumUtil.upZero(ReqInfoContext.getReqInfo().getMsgNum())) {
            map = notifyMsgDao.groupCountByUserIdAndStat(userId, NotifyStatEnum.UNREAD.getStat());
        }
        // 指定先后顺序
        Map<String, Integer> ans = new LinkedHashMap<>();
        initCnt(NotifyTypeEnum.COMMENT, map, ans);
        initCnt(NotifyTypeEnum.REPLY, map, ans);
        initCnt(NotifyTypeEnum.PRAISE, map, ans);
        initCnt(NotifyTypeEnum.COLLECT, map, ans);
        initCnt(NotifyTypeEnum.FOLLOW, map, ans);
        initCnt(NotifyTypeEnum.SYSTEM, map, ans);
        return ans;
    }

    private void initCnt(NotifyTypeEnum type, Map<Integer, Integer> map, Map<String, Integer> result) {
        result.put(type.name().toLowerCase(), map.getOrDefault(type.getType(), 0));
    }

    @Override
    public void saveArticleNotify(NotifyMsgEvent<UserFootDO> event) {
        UserFootDO foot = event.getContent();
        NotifyTypeEnum notifyType = event.getNotifyType();
        NotifyMsgDO msg = new NotifyMsgDO().setRelatedId(foot.getDocumentId())
                .setNotifyUserId(foot.getDocumentUserId())
                .setOperateUserId(foot.getUserId())
                .setType(notifyType.getType() )
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg("");

        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record == null) {
            // 若之前已经有对应的通知，则不重复记录；因为一个用户对一篇文章，可以重复的点赞、取消点赞，但是最终我们只通知一次
            notifyMsgDao.save(msg);

            // 消息通知
            notifyToUser(msg.getNotifyUserId(), String.format("太棒了，您的%s %s数+1!!!",
                    Objects.equals(foot.getDocumentType(), DocumentTypeEnum.ARTICLE.getCode()) ? "文章" : "评论",
                    event.getNotifyType().getMsg()));

            if(notifyType == NotifyTypeEnum.COLLECT) {
                RedisClient.hIncr(CountConstants.USER_STATISTIC_INFO + foot.getDocumentUserId(), CountConstants.COLLECTION_COUNT, 1);
                RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + foot.getDocumentId(), CountConstants.COLLECTION_COUNT, 1);
            }
            else if(notifyType == NotifyTypeEnum.PRAISE){
                RedisClient.hIncr(CountConstants.USER_STATISTIC_INFO + foot.getDocumentUserId(), CountConstants.PRAISE_COUNT, 1);
                RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + foot.getDocumentId(), CountConstants.PRAISE_COUNT, 1);
            }
        }
    }

    public void saveCommentNotify(NotifyMsgEvent<CommentDO> event) {

        CommentDO comment = event.getContent();
        ArticleDO article = articleReadService.queryBasicArticle(comment.getArticleId());
        NotifyMsgDO msg = new NotifyMsgDO().setNotifyUserId(article.getUserId())
                .setOperateUserId(comment.getUserId())
                .setRelatedId(article.getId())
                .setType(event.getNotifyType().getType())
                .setState(NotifyStatEnum.UNREAD.getStat()).setMsg(comment.getContent());

        notifyMsgDao.save(msg);

        // 消息通知
        notifyToUser(msg.getNotifyUserId(), String.format("文章《%s》收到一个新的评论，快去看看吧", article.getTitle()));

        RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + comment.getArticleId(), CountConstants.COMMENT_COUNT, 1);
    }

    public void saveReplyNotify(NotifyMsgEvent<CommentDO> event){
        NotifyMsgDO msg = new NotifyMsgDO();
        CommentDO comment = event.getContent();
        CommentDO parent = commentReadService.queryComment(comment.getParentCommentId());
        msg.setNotifyUserId(parent.getUserId())
                .setOperateUserId(comment.getUserId())
                .setRelatedId(comment.getArticleId())
                .setType(event.getNotifyType().getType())
                .setState(NotifyStatEnum.UNREAD.getStat()).setMsg(comment.getContent());
        // 回复同样支持多次回复，不做幂等校验
        notifyMsgDao.save(msg);

        // 消息通知
        notifyToUser(msg.getNotifyUserId(), String.format("您的评价《%s》收到一个新的回复，快去看看吧", parent.getContent()));

        RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + comment.getArticleId(), CountConstants.COMMENT_COUNT, 1);
    }

    public void removeArticleNotify(NotifyMsgEvent<UserFootDO> event) {
        UserFootDO foot = event.getContent();
        NotifyTypeEnum notifyType = event.getNotifyType();
        NotifyMsgDO msg = new NotifyMsgDO()
                .setRelatedId(foot.getDocumentId())
                .setNotifyUserId(foot.getDocumentUserId())
                .setOperateUserId(foot.getUserId())
                .setType(event.getNotifyType().getType())
                .setMsg("");
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record != null) {
            notifyMsgDao.removeById(record.getId());

            if(notifyType == NotifyTypeEnum.CANCEL_PRAISE) {
                RedisClient.hIncr(CountConstants.USER_STATISTIC_INFO + foot.getDocumentUserId(), CountConstants.PRAISE_COUNT, -1);
                RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + foot.getDocumentId(), CountConstants.PRAISE_COUNT, -1);
            }
            else if(notifyType == NotifyTypeEnum.CANCEL_COLLECT) {
                RedisClient.hIncr(CountConstants.USER_STATISTIC_INFO + foot.getDocumentUserId(), CountConstants.COLLECTION_COUNT, -1);
                RedisClient.hIncr(CountConstants.ARTICLE_STATISTIC_INFO + foot.getDocumentId(), CountConstants.COLLECTION_COUNT, -1);
            }
        }
    }

    // -------------------------------------------- 下面是与用户的websocket长连接维护相关实现 -------------------------

    /**
     * x用户发送
     * @param userId 用户id
     * @param msg 通知内容
     */
    @Override
    public void notifyToUser(Long userId, String msg) {
        wsUserSessionCache.getUnchecked(userId).forEach(s -> {
            WebSocketResponseUtil.sendMsgToUser(s, NOTIFY_TOPIC, msg);
        });
    }

    /**
     * 用户建立连接时，添加用户信息
     *
     * @param userId  用户id
     * @param session jwt token
     */
    private void addUserToken(Long userId, String session) {
        wsUserSessionCache.getUnchecked(userId).add(session);
    }

    /**
     * 断开连接时，移除用户信息
     *
     * @param userId  用户id
     * @param session jwt token
     */
    private void releaseUserToken(Long userId, String session) {
        wsUserSessionCache.getUnchecked(userId).remove(session);
    }

    /**
     * WebSocket通道管理
     *
     * @param accessor
     */
    @Override
    public void notifyChannelMaintain(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (StringUtils.isBlank(destination) || accessor.getCommand() == null) {
            return;
        }


        // 全局私信、通知长连接入口
        ReqInfoContext.ReqInfo user = (ReqInfoContext.ReqInfo) accessor.getUser();
        if (user == null) {
            log.info("websocket用户未登录! {}", accessor);
            return;
        }
        switch (accessor.getCommand()) {
            case SUBSCRIBE:
                // 建立用户通信通道
                addUserToken(user.getUserId(), user.getSession());
                break;
            case DISCONNECT:
                // 中断链接，去掉用户的长连接会话
                releaseUserToken(user.getUserId(), user.getSession());
                break;
        }
    }
}
