package org.birdbro.lumen.us.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * The US station deployment properties (us-order-exam-room-sev task 2.5/3.2): the
 * knobs the DEPLOYMENT declares about which department this station serves. The
 * committed yml ships NO default ON PURPOSE - an absent binding reads as NULL and the
 * consumers fail closed (the room directory answers empty, every create is refused
 * 检查室不存在或未启用) - the station never guesses a department.
 *
 * @author birdbro
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumen.us")
public class UsStationProperties {

    /**
     * The department this station serves (the room-directory scope + the create-time
     * room validation match field). NULL = unbound (fail closed everywhere).
     */
    private Long stationDeptId;
}
