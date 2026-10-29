package org.birdbro.common.core.pojo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * @author birdbro
 * @date 14:32 2024-05-31
 **/
@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class LogInfo implements Serializable {

    private String account;

    private Integer uid;


    private String title;


    private String descr;


    private String type;


    private Date actionTime;


    private Long spent;


    private String method;


    private String pack;


    private String url;


    private String session;


    private String exception;


    private Boolean mobile;


    private String ip;


    private String os;


    private String browser;


    private String engine;


    private Integer record;


    private String name;


    private String param;


    private String result;
}
