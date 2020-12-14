package com.alibaba.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 允许覆盖bean  beanName相同 以后者为准
 */
public class AllowBeanDefinitionOverridingEnvironmentProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> paramMap = new HashMap<>(4);
        paramMap.put("spring.main.allow-bean-definition-overriding","true");
        MapPropertySource mapPropertySource = new MapPropertySource("allowBeanDefinitionOverridingPropSource", paramMap);
        environment.getPropertySources().addLast(mapPropertySource);
    }
}
