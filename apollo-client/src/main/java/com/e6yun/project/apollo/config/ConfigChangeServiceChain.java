package com.e6yun.project.apollo.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static org.springframework.core.OrderComparator.sort;


public class ConfigChangeServiceChain implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    public static LinkedList<ConfigChangeService> configChangeServices;
    @Value("${e6yun.common.apollo.change.service.chain.ignore.bean.name.set:}")
    private Set<String> ignoredBeanNames;

    @PostConstruct
    public void init() {
        Map<String, ConfigChangeService> filterMap = applicationContext.getBeansOfType(ConfigChangeService.class);
        filterMap.keySet().removeIf(key -> ignoredBeanNames.contains(key));
        configChangeServices = new LinkedList<>(filterMap.values());
        sort(configChangeServices);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ConfigChangeServiceChain.applicationContext = applicationContext;
    }
}

