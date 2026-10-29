package org.birdbro.common.core.pojo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * @author birdbro
 * @date 16:20 2024/11/1
 **/
@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class LogSign implements Serializable {

    private String msg;

    private String descr;

    private Integer type;

    private Integer uid;

    private String account;

    private Date actionTime;

    private Boolean mobile;

    private String ip;

    private String os;

    private String browser;


    private String engine;


    private String session;


    private String token;


    private String remark;
}
