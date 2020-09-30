package com.alibaba.project.loadbalance;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import org.springframework.context.annotation.Bean;

/**
 * @author wangbaojie@e6yun.com
 * @date 2020/9/30 15:36
 * 这个类不加注解，所以不会自动被加载，需要一个Configuration类来用注解加载：@RibbonClients(defaultConfiguration = E6DefaultRibbonConfig.class)
 */
public class DefaultRibbonConfig {
    @Bean
    public ILoadBalancer ribbonLoadBalancer(IClientConfig config, ServerList<Server> serverList, ServerListFilter<Server> serverListFilter, IRule rule, IPing ping, ServerListUpdater serverListUpdater, ConfigFileLoader configFileLoader) {
        return new LoadBalancer(config, rule, ping, serverList, serverListFilter, serverListUpdater,configFileLoader);
    }
    @Bean
    public IRule ribbonRule(ConfigFileLoader configFileLoader) {
        LoadBalancerRule myRule = new LoadBalancerRule();
        myRule.init(configFileLoader);
        return myRule;
    }
}
