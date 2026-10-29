package org.birdbro.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.common.utils.SecurityUtils;
import org.springframework.context.annotation.Configuration;

/**
 * @author birdbro
 * @date 14:42 2024-05-06
 **/
@Configuration
public class MetaObjectConfig implements MetaObjectHandler {


    @Override
    public void insertFill(MetaObject metaObject) {
        UserHeader userHeader = SecurityUtils.getUserHeader();
        if (GadgetUtils.isNotNull(userHeader)) {
            if (GadgetUtils.isNotNull(userHeader.getUid())) {
                this.strictInsertFill(metaObject, "createBy", Integer.class, userHeader.getUid());
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        UserHeader userHeader = SecurityUtils.getUserHeader();
        if (GadgetUtils.isNotNull(userHeader)) {
            if (GadgetUtils.isNotNull(userHeader.getUid())) {
                this.strictInsertFill(metaObject, "updateBy", Integer.class, userHeader.getUid());
            }
        }
    }
}
