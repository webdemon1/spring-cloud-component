package com.alibaba.project;

import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.Map;

/**
 * jpa entityManagerFactoryPrimary4对象构造器
 */
public class EntityManagerFactoryBean extends LocalContainerEntityManagerFactoryBean {

    private static HibernateProperties hibernateProperties = new HibernateProperties();

    /**
     * 核心方法实现EntityManagerFactoryBean的创建
     * @param dataSource
     * @param builder
     * @param jpaProperties
     * @param dataSourceName
     * @param modelPackage
     * @return
     */
    public static LocalContainerEntityManagerFactoryBean getInit(DataSource dataSource, EntityManagerFactoryBuilder builder, JpaProperties jpaProperties,String dataSourceName,HibernateProperties hibernateProperties,String ... modelPackage){
        return builder
                .dataSource(dataSource)
                .properties(getVendorProperties(jpaProperties,hibernateProperties))
                .packages(modelPackage)
                .persistenceUnit("primaryPersistenceUnit4"+dataSourceName)
                .build();
    }

    private static Map<String, Object> getVendorProperties(JpaProperties jpaProperties,HibernateProperties hibernateProperties) {
        return hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(),new HibernateSettings());
    }
}
