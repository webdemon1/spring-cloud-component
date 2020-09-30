package com.alibaba.project.loadbalance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wangbaojie@e6yun.com
 * @date 2020/9/30 15:40
 * 自定义loadBalance开关和配置类，默认关闭，需要通过设置e6.rpc.config.local.enable=true打开这个功能
 */
@ConditionalOnProperty(value = "e6.rpc.config.local.enable",havingValue = "true",matchIfMissing = false)
@Configuration
@RibbonClients(defaultConfiguration = DefaultRibbonConfig.class)
public class LoadBalancerConfig {

    @Bean
    public ConfigFileLoader initConfigFileLoader(){
        ConfigFileLoader configFileLoader = new ConfigFileLoader();
        configFileLoader.initialize();
        return configFileLoader;
    }
}
