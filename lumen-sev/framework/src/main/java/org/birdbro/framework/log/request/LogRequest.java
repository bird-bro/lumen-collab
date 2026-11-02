package org.birdbro.framework.log.request;

import lombok.Data;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * @author birdbro
 * @Date 2024-11-14 10:24
 */
@Data
public class LogRequest extends PageSizeConfig {

    private String account;

    private String operateStartTime;

    private String operateEndTime;
}
