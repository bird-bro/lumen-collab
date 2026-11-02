package org.birdbro.common.Constants;

/**
 * 缓存的key 常量
 *
 * @author birdbro
 */
public class CacheConstants {

    public final static String RAS_PRIVATE_KEY = "rasPrivateKey";

    public final static String RAS_PUBLIC_KEY = "rasPublicKey";


    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * redis的公共知识库key值
     */
    public final static String TEMP_PUBLIC_KEY = "temp_public:";

    /**
     * redis的私有知识库key值
     */
    public final static String TEMP_PRIVATE_KEY = "temp_private:";
}
