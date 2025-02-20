package com.github.paicoding.forum.api.model.vo.notify;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * @author YiHui
 * @date 2022/9/3
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(value = {"source"}, ignoreUnknown = true)

public class NotifyMsgEvent<T> extends ApplicationEvent {

    private NotifyTypeEnum notifyType;

    private T content;


    public NotifyMsgEvent(Object source, NotifyTypeEnum notifyType, T content) {
        super(source);
        this.notifyType = notifyType;
        this.content = content;
    }

    @JsonCreator
    public NotifyMsgEvent(@JsonProperty("notifyType") NotifyTypeEnum notifyType,
                          @JsonProperty("content") T content) {
        super("dummySource");
        this.notifyType = notifyType;
        this.content = content;
    }


}
