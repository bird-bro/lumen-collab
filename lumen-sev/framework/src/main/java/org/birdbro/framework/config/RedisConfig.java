package org.birdbro.framework.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * redis配置 - 合并缓存管理配置
 *
 * <p>lumen adaptation (a), init-backend-skeleton design D5: this configuration is
 * conditional so a service context also loads with no Redis server / no Redis jar
 * (Redis is optional in the hospital form).</p>
 *
 * @author ruoyi
 */
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
@ConditionalOnProperty(name = "lumen.redis.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport {

    /**
     * 缓存名称常量
     */
    public static final String CACHE_DICT = "dict";
    public static final String CACHE_ORG = "org";
    public static final String CACHE_DEPT = "dept";
    public static final String CACHE_USER = "user";
    public static final String CACHE_EXAM = "exam";
    public static final String CACHE_ROLE = "role";

    /**
     * 默认缓存过期时间（分钟）
     */
    private static final long DEFAULT_TTL = 30;
    private static final long DICT_TTL = 60;
    private static final long ORG_TTL = 30;
    private static final long USER_TTL = 15;

    @Bean
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        // Hash的key也采用StringRedisSerializer的序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置CacheManager，支持不同缓存的不同过期时间
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(DEFAULT_TTL))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 不同缓存的过期时间配置
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>();
        configMap.put(CACHE_DICT, defaultConfig.entryTtl(Duration.ofMinutes(DICT_TTL)));
        configMap.put(CACHE_ORG, defaultConfig.entryTtl(Duration.ofMinutes(ORG_TTL)));
        configMap.put(CACHE_DEPT, defaultConfig.entryTtl(Duration.ofMinutes(ORG_TTL)));
        configMap.put(CACHE_USER, defaultConfig.entryTtl(Duration.ofMinutes(USER_TTL)));
        configMap.put(CACHE_EXAM, defaultConfig.entryTtl(Duration.ofMinutes(DICT_TTL)));
        configMap.put(CACHE_ROLE, defaultConfig.entryTtl(Duration.ofMinutes(ORG_TTL)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .transactionAware()
                .build();
    }

    @javax.annotation.Resource
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * Jackson序列化器配置
     */
    private Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        serializer.setObjectMapper(mapper);
        return serializer;
    }

    @Bean
    public DefaultRedisScript<Long> limitScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(limitScriptText());
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * 限流脚本
     */
    private String limitScriptText() {
        return "local key = KEYS[1]\n" +
                "local count = tonumber(ARGV[1])\n" +
                "local time = tonumber(ARGV[2])\n" +
                "local current = redis.call('get', key);\n" +
                "if current and tonumber(current) > count then\n" +
                "    return tonumber(current);\n" +
                "end\n" +
                "current = redis.call('incr', key)\n" +
                "if tonumber(current) == 1 then\n" +
                "    redis.call('expire', key, time)\n" +
                "end\n" +
                "return tonumber(current);";
    }
}
