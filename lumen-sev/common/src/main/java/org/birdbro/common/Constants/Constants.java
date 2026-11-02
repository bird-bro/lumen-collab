package org.birdbro.common.Constants;


/**
 * 通用常量信息
 *
 * @author birdbro
 */
public class Constants {

    public final static Integer APP_CODE = 101802;

    public final static Integer DEFAULT_INT = 0;

    public final static Integer ORG_MAIN = 152001;

    /**
     * UTF-8 字符集
     */
    public static final String UTF8 = "UTF-8";

    /**
     * GBK 字符集
     */
    public static final String GBK = "GBK";

    /**
     * 通用成功标识
     */
    public static final Integer SUCCESS = 10;

    /**
     * 通用失败标识
     */
    public static final Integer FAIL = 11;

    /**
     * 通用失败标识
     */
    public static final Integer SIGN_OUT = 12;


    /**
     * 所有权限标识
     */
    public static final String ALL_PERMISSION = "*:*:*";

    /**
     * 管理员角色权限标识
     */
    public static final String SUPER_ADMIN = "admin";

    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 令牌前缀
     */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /**
     * 资源映射路径 前缀
     */
    public static final String RESOURCE_PREFIX = "/profile";

    /**
     * 自动识别json对象白名单配置（仅允许解析的包名，范围越小越安全）
     */
    public static final String[] JSON_WHITELIST_STR = {"org.springframework", "com.ruoyi"};


    /**
     * 加密方式
     **/
    public static final String ALGORITHMNAME = "md5";

    /**
     * 加密系数
     **/
    public static final String HASHITERATIONS = "2";

    /**
     * 加密盐值
     **/
    public static final String SALT = "ABC";
}
