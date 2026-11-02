package org.birdbro.common.utils;

import com.alibaba.fastjson2.JSONObject;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.common.exception.Assert;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.http.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;

/**
 * 安全服务工具类
 *
 * @author birdbro
 */
public class SecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

    /**
     * 用户ID
     **/
    public static Integer getUserId() {
        UserLogin loginUser = getLoginUser();
        Assert.notNull(loginUser, ExceptionEnum.ERROR_A0301, "用户未登录，无法获取用户ID");
        return loginUser.getUserId();
    }

    /**
     * 获取用户
     **/
    public static UserLogin getLoginUser() {
        Authentication authentication = getAuthentication();
        Assert.notNull(authentication, ExceptionEnum.ERROR_A0301, "用户未认证");
        Object principal = authentication.getPrincipal();
        Assert.notNull(principal, ExceptionEnum.ERROR_A0301, "获取用户信息失败");
        return (UserLogin) principal;
    }

    /**
     * 获取Authentication
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 当前登录用户归属机构 OID（不抛异常）。
     * 取入职机构（JWT user.entry.oid，源自 user_join.oid）——用户归属机构以入职关系为准。
     * 注意：任职/代理机构列表 user.titleValue 主要供前端下拉框数据权限使用；
     * 后端 OrgInfoServiceImpl#pageSmallByInitial 亦会服务端解析它参与 initial 查询范围校验（越权拒绝）。
     * 未登录或会话缺 entry 等情况返回 0，调用方按 0 视为「无归属机构」自行处理（如 TaskController#create 拒绝创建）。
     *
     * @return 归属机构 OID；无法确定时返回 0
     */
    public static int getUserOid() {
        try {
            Authentication authentication = getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UserLogin)) {
                log.warn("getUserOid 返回 0：无认证或 principal 非 UserLogin（用户未登录 / 会话已过期）");
                return 0;
            }
            UserLogin login = (UserLogin) authentication.getPrincipal();
            UserSign user = login.getUser();
            if (user == null) {
                log.warn("getUserOid 返回 0：UserSign 为空，uid={}", login.getUserId());
                return 0;
            }
            if (user.getEntry() != null && user.getEntry().getOid() != null) {
                return user.getEntry().getOid();
            }
            // entry 缺失通常源于登录会话未补全入职机构（旧包 / 小程序路径），调用方应拒绝写库
            log.warn("getUserOid 返回 0：会话缺 entry.oid，uid={}, title={}", login.getUserId(), user.getTitle());
        } catch (Exception e) {
            // JWT 内容缺失等任何异常都按无归属机构处理，绝不向上抛
            log.warn("getUserOid 返回 0：解析异常 {}", e.getMessage());
            return 0;
        }
        return 0;
    }

    /**
     * 密码加密
     *
     * @author birdbro
     * @since: 2024-08-02 16:51
     * @param:
     * @return:
     **/
    public static String getEncryptPassword(String accountName, String password) {
        return new SimpleHash(Constants.ALGORITHMNAME, password,
                ByteSource.Util.bytes(accountName + Constants.SALT), Integer.parseInt(Constants.HASHITERATIONS)).toHex();
    }

    
    /**
     * 获取用户请求头信息
     *
     * @author birdbro
     * @since: 2024-08-12 11:31
     * @param:
     * @return:
     **/
    public static UserHeader getUserHeader() {
        try {
            HttpServletRequest request = HttpHelper.getHttpServletRequest();
            String userHeader = URLDecoder.decode(request.getHeader("USER-HEADER"), Constants.UTF8);
            return JSONObject.parseObject(userHeader, UserHeader.class);
        } catch (Exception e) {
            return null;
        }
    }

}
