package org.birdbro.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 配置类
 * 优化分页查询性能
 *
 * @author birdbro
 * @date 10:02 2022-12-9
 **/
@EnableTransactionManagement
@Configuration
public class MybatisPlusConfig {

    /**
     * 最大分页限制，防止深分页性能问题
     */
    public static final long MAX_LIMIT = 10000;

    /**
     * 注册乐观锁和分页插件(新版：3.4.0)
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        // 数据库类型：PostgreSQL
        paginationInnerInterceptor.setDbType(DbType.POSTGRE_SQL);
        // 分页溢出处理：true=页码超过总页数时跳回首页重查，false=直接返回空
        paginationInnerInterceptor.setOverflow(false);
        // 设置最大单页限制数量，防止内存溢出
        paginationInnerInterceptor.setMaxLimit(MAX_LIMIT);
        // count 优化（裁剪 LEFT JOIN）：保持 false。true 会裁掉未出现在 WHERE 的一对多
        // LEFT JOIN（如 ledger_audit、user_join），导致 count 与分页数据行数不一致
        paginationInnerInterceptor.setOptimizeJoin(false);

        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 开启驼峰命名自动转换（数据库下划线 -> Java 驼峰）
     */
    @Bean
    public MybatisConfiguration mybatisConfiguration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        return configuration;
    }
}
