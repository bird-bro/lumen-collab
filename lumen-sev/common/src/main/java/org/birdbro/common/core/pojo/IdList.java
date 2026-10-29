package org.birdbro.common.core.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author birdbro
 * @date 10:47 2024-06-13
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class IdList {

    List<Integer> ids;
}
