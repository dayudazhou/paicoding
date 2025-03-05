package com.github.paicoding.forum.api.model.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.github.paicoding.forum.api.model.vo.seo.Seo;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import lombok.Data;

import java.security.Principal;

/**
 * 请求上下文，携带用户身份相关信息
 *
 * @author YiHui
 * @date 2022/7/6
 */
public class ReqInfoContext {
    // 使用了 Alibaba 的 TransmittableThreadLocal 来保存请求上下文数据。
    // 这种 ThreadLocal 的变种不仅能在当前线程中传递数据，
    // 还能在使用线程池等场景下将上下文传递给子线程，确保整个请求链路中的数据一致性。
    private static TransmittableThreadLocal<ReqInfo> contexts = new TransmittableThreadLocal<>();

    /**
     * 将构造好的 ReqInfo 对象放入线程上下文中，
     * 以便后续在请求链路中通过 ReqInfoContext.getReqInfo() 获取。
     * @param reqInfo
     */
    public static void addReqInfo(ReqInfo reqInfo) {
        contexts.set(reqInfo);
    }

    /**
     * 在请求处理结束后调用，用于清除线程上下文中的数据，防止内存泄漏或数据污染
     */
    public static void clear() {
        contexts.remove();
    }

    /**
     * 用于获取当前线程中存储的 ReqInfo 对象，
     * 这样在业务逻辑中可以随时访问请求的相关信息
     * @return
     */
    public static ReqInfo getReqInfo() {
        return contexts.get();
    }

    @Data
    // 实现了 Principal 接口，这样它可以作为用户身份标识在安全认证框架中使用
    public static class ReqInfo implements Principal {
        /**
         * appKey
         * 请求的应用标识
         */
        private String appKey;
        /**
         * 访问的域名
         */
        private String host;
        /**
         * 访问路径
         */
        private String path;
        /**
         * 客户端ip
         */
        private String clientIp;
        /**
         * referer
         * 来源页面信息
         */
        private String referer;
        /**
         * post 表单参数
         * POST 表单参数，存储请求体数据
         */
        private String payload;
        /**
         * 设备信息
         */
        private String userAgent;

        /**
         * 登录的会话
         */
        private String session;

        /**
         * 用户id
         */
        private Long userId;
        /**
         * 用户信息
         */
        private BaseUserInfoDTO user;
        /**
         * 消息数量
         */
        private Integer msgNum;

        private Seo seo;

        private String deviceId;

        /**
         * 当前聊天的会话id
         */
        private String chatId;

        @Override
        public String getName() {
            return session;
        }
    }
}
