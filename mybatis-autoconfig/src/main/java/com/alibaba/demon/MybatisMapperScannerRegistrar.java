package com.alibaba.demon;

import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * mapper 扫描
 */
public class MybatisMapperScannerRegistrar extends MapperScannerRegistrar {
    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        try {
            Consumer<BeanDefinitionRegistry> userConsumer = mybatisRegistry -> {
                for (Map.Entry<String, Map<String,String>> entry : MybatisDataSourceConfiguration.datasource.entrySet()) {
                    String dataSourceName = entry.getKey();
                    Map<String,String> prop = entry.getValue();
                    if(Objects.equals(prop.get(Constant.MYBATIS_USE),Boolean.FALSE.toString())){
                        continue;
                    }
                    MybatisDataSourceConfiguration.setDefaultMybatisScanAndPackage(dataSourceName,prop);
                    ClassPathMapperScanner scanner = new ClassPathMapperScanner(mybatisRegistry);
                    if (this.resourceLoader != null) {
                        scanner.setResourceLoader(this.resourceLoader);
                    }
                    scanner.setSqlSessionTemplateBeanName(dataSourceName+"SqlSessionTemplate");
                    scanner.setSqlSessionFactoryBeanName("");
                    scanner.registerFilters();
                    scanner.doScan(prop.get(Constant.MYBATIS_SCAN).split(","));
                }
            };
            MybatisDataSourceConfiguration.addMybatisConsumer(registry,userConsumer);
        } catch (Exception e) {
            throw new DynamicDataSourceException("mybatis-mapper-path配置出错，请检查");
        }

    }
    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }


}
