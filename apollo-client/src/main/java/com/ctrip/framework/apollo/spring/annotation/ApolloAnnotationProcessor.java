package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 修改了 processMethod 方法 使之能够监听 配置文件中的配置变化  不再局限于 注解
 * 相较于源码中的ApolloAnnotationProcessor 额外实现了ApplicationContextAware 用以获取applicationContext
 * 因为 本身 ApolloAnnotationProcessor 不归spring ioc容器管理 ，只能通过applicationContext来获取配置信息
 */
public class ApolloAnnotationProcessor extends ApolloProcessor implements ApplicationContextAware {
    Logger logger = LoggerFactory.getLogger(ApolloAnnotationProcessor.class);

    private ApplicationContext applicationContext;

    @Override
    protected void processField(Object bean, String beanName, Field field) {
        ApolloConfig annotation = AnnotationUtils.getAnnotation(field, ApolloConfig.class);
        if (annotation == null) {
            return;
        }

        Preconditions.checkArgument(Config.class.isAssignableFrom(field.getType()),
                "Invalid type: %s for field: %s, should be Config", field.getType(), field);

        String namespace = annotation.value();
        Config config = ConfigService.getConfig(namespace);

        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, config);
    }

    @Override
    protected void processMethod(final Object bean, String beanName, final Method method) {
        ApolloConfigChangeListener annotation = AnnotationUtils
                .findAnnotation(method, ApolloConfigChangeListener.class);
        if (annotation == null) {
            return;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        Preconditions.checkArgument(parameterTypes.length == 1,
                "Invalid number of parameters: %s for method: %s, should be 1", parameterTypes.length,
                method);
        Preconditions.checkArgument(ConfigChangeEvent.class.isAssignableFrom(parameterTypes[0]),
                "Invalid parameter type: %s for method: %s, should be ConfigChangeEvent", parameterTypes[0],
                method);
        ReflectionUtils.makeAccessible(method);
        String[] namespaces = annotation.value();
        Environment environment = applicationContext.getBean(Environment.class);
        //先拿配置中的value  如果没有 查询是否要 监听全部 namespace  如果不是  就 取默认
        String property = environment.getProperty("common.apollo.client.config.listener.value");
        if (!StringUtils.isEmpty(property)) {
            logger.info("检测到使用了 listener 数组 监听namespace配置变化：{}", property);
            String[] apolloConfigChangeListenerValue = property.split(",");
            if (apolloConfigChangeListenerValue.length != 0) {
                namespaces = apolloConfigChangeListenerValue;
            }
        } else {
            String isListenAllNameSpace = environment.getProperty("common.apollo.client.config.listen.all.namespace");
            String[] apolloConfigChangeListenerValue = null;
            if (Objects.equal(Boolean.TRUE.toString(), isListenAllNameSpace)) {
                property = environment.getProperty("apollo.bootstrap.namespaces");
                logger.info("检测到监听全部namespace配置变化 {}", property);
                apolloConfigChangeListenerValue = property.split(",");
            }

            if (apolloConfigChangeListenerValue != null && apolloConfigChangeListenerValue.length != 0) {
                namespaces = apolloConfigChangeListenerValue;
            }
        }
        ConfigChangeListener configChangeListener = new ConfigChangeListener() {
            @Override
            public void onChange(ConfigChangeEvent changeEvent) {
                ReflectionUtils.invokeMethod(method, bean, changeEvent);
            }
        };

        for (String namespace : namespaces) {
            Config config = ConfigService.getConfig(namespace);

            config.addChangeListener(configChangeListener);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}