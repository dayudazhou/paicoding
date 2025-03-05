package com.github.paicoding.forum.service.user.service.help;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 密码加密器，后续接入SpringSecurity之后，可以使用 PasswordEncoder 进行替换
 *
 * @author YiHui
 * @date 2022/12/5
 */
@Component
public class UserPwdEncoder {
    /**
     * 密码加盐，更推荐的做法是每个用户都使用独立的盐，提高安全性
     */
    @Value("${security.salt}")
    private String salt;

    // 代表在原密码index=3的位置插入盐值
    @Value("${security.salt-index}")
    private Integer saltIndex;

    public boolean match(String plainPwd, String encPwd) {
        return Objects.equals(encPwd(plainPwd), encPwd);
    }

    /**
     * 明文密码处理
     *
     * @param plainPwd
     * @return
     */
    public String encPwd(String plainPwd) {
        // 加盐
        // 如果长度大于加盐位置，那么就插入到相应位置
        // 否则直接插入到最后
        if (plainPwd.length() > saltIndex) {
            plainPwd = plainPwd.substring(0, saltIndex) + salt + plainPwd.substring(saltIndex);
        } else {
            plainPwd = plainPwd + salt;
        }
        // md5加密
        return DigestUtils.md5DigestAsHex(plainPwd.getBytes(StandardCharsets.UTF_8));
    }

}
