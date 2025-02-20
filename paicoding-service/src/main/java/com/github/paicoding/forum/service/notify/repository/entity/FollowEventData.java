package com.github.paicoding.forum.service.notify.repository.entity;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FollowEventData {
    // 关注关系信息
    private UserRelationDO relation;
    // 当前发布事件的用户ID（例如当前登录用户）
    private Long publisherUserId;
}

