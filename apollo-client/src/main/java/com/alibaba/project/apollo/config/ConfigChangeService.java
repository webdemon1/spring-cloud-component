package com.alibaba.project.apollo.config;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import org.springframework.core.Ordered;

public interface ConfigChangeService extends Ordered {
    /**
     * 自定义配置监听在此实现
     *
     * @param changeEvent
     */
    void detectConfigChanges(ConfigChangeEvent changeEvent);
}
