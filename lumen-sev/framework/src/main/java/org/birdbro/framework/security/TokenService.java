package org.birdbro.framework.security;


import com.alibaba.fastjson2.JSON;
import eu.bitwalker.useragentutils.UserAgent;
import org.birdbro.framework.utils.UserAgents;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.birdbro.common.Constants.CacheConstants;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.user.AgentInfo;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.redis.RedisCache;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.common.utils.IpUtils;
import org.birdbro.common.utils.ServletUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * token验证处理
 *
 * @author birdbro
 */
@Slf4j
@Component
public class TokenService {
    protected static final long MILLIS_SECOND = 1000;
    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;
    /**
     * 令牌自定义标识
     */
    @Value("${token.header}")
    private String header;

    /**
     * 令牌秘钥
     */
    @Value("${token.secret}")
    private String secret;

    /**
     * 令牌有效期（默认30分钟）
     */
    @Value("${token.expireTime}")
    private int expireTime;
    @Resource
    private RedisCache redisCache;


    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public UserLogin getLoginUser(HttpServletRequest request) {
        // 获取请求携带的令牌
        String token = getToken(request);
        if (GadgetUtils.isEmpty(token)) {
            return null;
        }

        try {
            Claims claims = parseToken(token);
            // 解析用户信息
            String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
            if (uuid == null) {
                // Token 缺少用户标识 -> 视为未认证，返回 null（由 AuthenticationEntryPoint 返回 203）
                log.warn("Token中未包含用户标识");
                return null;
            }

            String userKey = getTokenKey(uuid);
            Object cacheObject = redisCache.getCacheObject(userKey);
            if (cacheObject == null) {
                // Redis 会话已过期 -> 视为未认证，返回 null（由 AuthenticationEntryPoint 返回 203）
                log.warn("登录状态已过期，请重新登录");
                return null;
            }

            String info = cacheObject.toString();
            return JSON.parseObject(info, UserLogin.class);
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            log.warn("Token格式非法: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("获取用户信息异常: {}", e.getMessage());
            return null;
        }
    }


    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取请求token
     *
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);
        if (GadgetUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }


    private String getTokenKey(String uuid) {
        return CacheConstants.LOGIN_TOKEN_KEY + uuid;
    }

    /**
     * 刷新令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshToken(UserLogin loginUser) {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    /**
     * 设置用户代理信息
     *
     * @param loginUser 登录信息
     */
    public void setUserAgent(UserLogin loginUser) {
        // lumen adaptation (10): null-safe UA parse (hardening - no observed defect here,
        // the shared entry point keeps all three call sites on one semantics)
        UserAgent userAgent = UserAgents.fromHeader(
                ServletUtils.getRequest().getHeader("User-Agent"));

        String ip = IpUtils.getIpAddr();
        AgentInfo agent = AgentInfo.builder()
                .ipaddr(ip)
                .location(IpUtils.getRealAddressByIP(ip))
                .browser(userAgent.getBrowser().getName())
                .os(userAgent.getOperatingSystem().getName())
                .build();
        loginUser.setAgent(agent);
    }

    /**
     * 从数据声明生成令牌
     *
     * @param claims 数据声明
     * @return 令牌
     */
    private String createToken(Map<String, Object> claims) {
        String token = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, secret).compact();
        return token;
    }

    /**
     * 创建令牌
     *
     * @param loginUser 用户信息
     * @return 令牌
     */
    public String createToken(UserLogin loginUser) {
        String token = UUID.randomUUID().toString();
        loginUser.setToken(token);
        setUserAgent(loginUser);
        refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, token);
        return createToken(claims);
    }


    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存
     *
     * @param loginUser
     * @return 令牌
     */
    public void verifyToken(UserLogin loginUser) {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN) {
            refreshToken(loginUser);
        }
    }


    /**
     * 删除用户身份信息
     */
    public void delLoginUser(String token) {
        if (StringUtils.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }
}
