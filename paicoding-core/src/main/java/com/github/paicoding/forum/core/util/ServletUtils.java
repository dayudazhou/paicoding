package com.github.paicoding.forum.core.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Servlet工具类
 */
public class ServletUtils {

    /**
     * 获取当前的 HttpServletRequest
     *
     * @return HttpServletRequest
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes();

        // 返回当前的 HttpServletRequest 对象
        return attributes.getRequest();
    }
}
