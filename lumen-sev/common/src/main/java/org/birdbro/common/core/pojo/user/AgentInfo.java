package org.birdbro.common.core.pojo.user;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author birdbro
 * @date 15:58 2024-05-27
 **/
@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class AgentInfo {

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String location;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

}
