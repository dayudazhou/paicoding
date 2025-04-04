package com.github.paicoding.forum.service.comment.service.impl;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.core.util.NumUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.converter.CommentConverter;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.comment.service.CommentWriteService;
import com.github.paicoding.forum.service.notify.service.RabbitmqService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.service.UserFootService;
import com.rabbitmq.client.BuiltinExchangeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * 评论Service
 *
 * @author louzai
 * @date 2022-07-24
 */
@Service
public class CommentWriteServiceImpl implements CommentWriteService {

    @Autowired
    private CommentDao commentDao;

    @Autowired
    private ArticleReadService articleReadService;

    @Autowired
    private UserFootService userFootWriteService;

    @Autowired
    private RabbitmqService rabbitmqService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveComment(CommentSaveReq commentSaveReq) {
        // 保存评论
        CommentDO comment;
        if (NumUtil.nullOrZero(commentSaveReq.getCommentId())) {
            comment = addComment(commentSaveReq);
        } else {
            comment = updateComment(commentSaveReq);
        }
        return comment.getId();
    }

    private CommentDO addComment(CommentSaveReq commentSaveReq) {
        // 0.获取父评论信息，校验是否存在
        Long parentCommentUser = getParentCommentUser(commentSaveReq.getParentCommentId());

        // 1. 保存评论内容
        CommentDO commentDO = CommentConverter.toDo(commentSaveReq);
        Date now = new Date();
        commentDO.setCreateTime(now);
        commentDO.setUpdateTime(now);
        commentDao.save(commentDO);

        // 2. 保存足迹信息 : 文章的已评信息 + 评论的已评信息
        // 获取评论对应的文章
        ArticleDO article = articleReadService.queryBasicArticle(commentSaveReq.getArticleId());
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.ARTICLE_NOT_EXISTS, commentSaveReq.getArticleId());
        }
        userFootWriteService.saveCommentFoot(commentDO, article.getUserId(), parentCommentUser);

        // 3. 发布添加/回复评论事件

        if(rabbitmqService.enabled()){
            NotifyMsgEvent<CommentDO> event = new NotifyMsgEvent<>(this, NotifyTypeEnum.COMMENT, commentDO);
            rabbitmqService.publishMsg(
                    CommonConstants.EXCHANGE_NAME_DIRECT,
                    BuiltinExchangeType.DIRECT,
                    CommonConstants.QUERE_KEY_PRAISE,
                    JsonUtil.toStr(event));
            if(NumUtil.upZero(parentCommentUser)){
                event = new NotifyMsgEvent<>(this, NotifyTypeEnum.REPLY, commentDO);
                rabbitmqService.publishMsg(
                        CommonConstants.EXCHANGE_NAME_DIRECT,
                        BuiltinExchangeType.DIRECT,
                        CommonConstants.QUERE_KEY_PRAISE,
                        JsonUtil.toStr(event));
            }
        }

        else{
            SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.COMMENT, commentDO));
            if (NumUtil.upZero(parentCommentUser)) {
                // 评论回复事件
                SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.REPLY, commentDO));
            }
        }

        return commentDO;
    }

    private CommentDO updateComment(CommentSaveReq commentSaveReq) {
        // 更新评论
        CommentDO commentDO = commentDao.getById(commentSaveReq.getCommentId());
        if (commentDO == null) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, commentSaveReq.getCommentId());
        }
        commentDO.setContent(commentSaveReq.getCommentContent());
        commentDao.updateById(commentDO);
        return commentDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        CommentDO commentDO = commentDao.getById(commentId);
        // 1.校验评论，是否越权，文章是否存在
        if (commentDO == null) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, "评论ID=" + commentId);
        }
        if (Objects.equals(commentDO.getUserId(), userId)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "无权删除评论");
        }
        // 获取文章信息
        ArticleDO article = articleReadService.queryBasicArticle(commentDO.getArticleId());
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.ARTICLE_NOT_EXISTS, commentDO.getArticleId());
        }

        // 2.删除评论、足迹
        commentDO.setDeleted(YesOrNoEnum.YES.getCode());
        commentDao.updateById(commentDO);
        userFootWriteService.removeCommentFoot(commentDO, article.getUserId(), getParentCommentUser(commentDO.getParentCommentId()));


        // 3. 发布删除评论事件
        if(rabbitmqService.enabled()){
            NotifyMsgEvent<CommentDO> event = new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_COMMENT, commentDO);
            rabbitmqService.publishMsg(
                    CommonConstants.EXCHANGE_NAME_DIRECT,
                    BuiltinExchangeType.DIRECT,
                    CommonConstants.QUERE_KEY_PRAISE,
                    JsonUtil.toStr(event));
            if (NumUtil.upZero(commentDO.getParentCommentId())){
                event = new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_REPLY, commentDO);
                rabbitmqService.publishMsg(
                        CommonConstants.EXCHANGE_NAME_DIRECT,
                        BuiltinExchangeType.DIRECT,
                        CommonConstants.QUERE_KEY_PRAISE,
                        JsonUtil.toStr(event));
            }
        }
        else{
            SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_COMMENT, commentDO));
            if (NumUtil.upZero(commentDO.getParentCommentId())) {
                // 评论
                SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_REPLY, commentDO));
            }
        }

    }


    private Long getParentCommentUser(Long parentCommentId) {
        // 如果parentId直接是空的就返回空值
        if (NumUtil.nullOrZero(parentCommentId)) {
            return null;

        }
        // 调用内置的getById方法获取这一行记录
        CommentDO parent = commentDao.getById(parentCommentId);
        // 没有这个父评论，抛出异常
        if (parent == null) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, "父评论=" + parentCommentId);
        }
        // 返回这个父评论的作者
        return parent.getUserId();
    }

}
