package org.birdbro.framework.security.handle;

import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.R;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.utils.ServletUtils;
import org.birdbro.framework.manager.AsyncManager;
import org.birdbro.framework.manager.factory.AsyncFactory;
import org.birdbro.framework.security.TokenService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 自定义退出处理类 返回成功
 *
 * @author birdbro
 */
@Configuration
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler {

    @Resource
    private TokenService tokenService;

    /**
     * 退出处理
     *
     * @return
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        UserLogin userLogin = tokenService.getLoginUser(request);
        // 仅有有效登录态时清理缓存并记退出日志；无/过期 token 时 getLoginUser 返回 null，直接返回成功
        // （原条件 isEmpty 写反：空 token 走删空键+取 user，且 userLogin 为 null 时直接 NPE）
        if (userLogin != null && StringUtils.isNotEmpty(userLogin.getToken())) {
            String account = userLogin.getUser().getAccount();
            // 删除Redis缓存记录
            tokenService.delLoginUser(userLogin.getToken());
            // 记录退出日志
            AsyncManager.me().execute(
                    AsyncFactory.recordForSign(
                            account,
                            Constants.SIGN_OUT,
                            "Sign-OUT",
                            "退出成功，用户" + account,
                            userLogin));
        }
        ServletUtils.renderString(response, JSON.toJSONString(R.ofSuccess("退出成功")));
    }
}
