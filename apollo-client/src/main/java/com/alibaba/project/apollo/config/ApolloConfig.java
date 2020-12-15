package com.alibaba.project.apollo.config;

import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedList;

/**
 * 默认加载的配置类
 */
@Configuration
@EnableApolloConfig
@ConditionalOnProperty(value = "apollo.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class ApolloConfig {

    Logger logger = LoggerFactory.getLogger(ConfigChangeService.class);

    /**
     * 检测配置变化
     *
     * @param changeEvent
     */
    @ApolloConfigChangeListener
    public void configChangeListener(ConfigChangeEvent changeEvent) {
        //配置变化处理
        LinkedList<ConfigChangeService> configChangeServices = ConfigChangeServiceChain.configChangeServices;
        if (configChangeServices == null || configChangeServices.size() == 0) {
            for (String key : changeEvent.changedKeys()) {
                ConfigChange change = changeEvent.getChange(key);
                logger.info("发现变化 - key: {}, oldValue: {}, newValue: {}, changeType: {}", change.getPropertyName(), change.getOldValue(), change.getNewValue(), change.getChangeType());
            }
        } else {
            for (ConfigChangeService configChangeService : configChangeServices) {
                configChangeService.detectConfigChanges(changeEvent);
            }
        }
    }

    @Bean
    public ConfigChangeServiceChain configChangeServiceChain() {
        return new ConfigChangeServiceChain();
    }
}
