package com.alibaba.project;


import com.alibaba.druid.pool.DruidDataSource;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 通过配置动态生成jpa 和 mybatis 的实现 核心
 * 实现了InitializingBean 做数据源配置，BeanPostProcessor 做初始化 如果不实现BeanPostProcessor会出现E6DynamicDataSourceConfiguration不能加载的问题
 */
@Configuration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@ConfigurationProperties(prefix = "spring")
@EnableJpaRepositories
public class JpaDataSourceConfiguration implements InitializingBean, ApplicationContextAware, BeanPostProcessor {
    static Logger logger = LoggerFactory.getLogger(JpaDataSourceConfiguration.class);
    public static Map<String, Map<String, String>> datasource = new HashMap<>();
    private static ApplicationContext applicationContext;
    /**
     * 默认数据源配置，如果自定义可以覆盖
     */
    private static Map<String, String> defaultDataSourcePropMap = new HashMap<>(9);

    @Value("${spring.jpa.show-sql:true}")
    public Boolean isShowSql;
    @Value("${main.class.package:com.e6yun.project}")
    public String defaultMainClassPackage;

    public static String primaryDataSource;

    public static String mainClassPackage;

    @Value("${spring.primaryDataSource:spring.datasource.primary}")
    public void setPrimaryDataSource(String primaryDataSource) {
        JpaDataSourceConfiguration.primaryDataSource = primaryDataSource;
    }
    /**
     * 核心方法
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        setMainClassPackage();
        //accept执行，此时sqlserver已经加载完毕
        //这两行执行注册 mybaitis

        jpaConsumerMap.entrySet().forEach(entity -> entity.getValue().accept(entity.getKey()));
        if (datasource.size() == 0) {
            throw new DynamicDataSourceException("数据源配置未填写");
        }
        //遍历配置信息
        for (Map.Entry<String, Map<String, String>> entry : datasource.entrySet()) {
            String dataSourceName = entry.getKey();
            Map<String, String> prop = entry.getValue();

            /**
             * 得到当前数据源
             */
            DruidDataSource dataSource = registerAndGetThisDataSource(dataSourceName, prop);
            if (dataSource == null) {
                throw new DynamicDataSourceException("springApplicationContext中不存在" + dataSourceName + "DataSource 请检查配置文件是否配置正确。");
            }

            /**
             * 构建jpa 环境
             */
            jpaConfigBuild(dataSource, dataSourceName, prop);
        }
    }

    /**
     * 该方法声明一个默认数据源
     * @param environment
     * @param primaryDsName
     * @return
     */
    @Primary
    @Bean(name = "primaryDataSource")
    public DruidDataSource primaryDataSource(Environment environment, @Value("${spring.primaryDataSource:spring.datasource.primary}") String primaryDsName) {
        logger.info("begin to create primaryDataSource primaryDsNamePrefix = {}"+ primaryDsName);
        DruidDataSource dataSource= new DruidDataSource();
        String url = environment.getProperty(primaryDsName + ".url");
        String username = environment.getProperty(primaryDsName + ".username");
        String password = environment.getProperty(primaryDsName + ".password");
        String rsaPassword = environment.getProperty(primaryDsName + ".rsaPassword");
        String driver = environment.getProperty(primaryDsName + ".driverClassName");
        if(StringUtils.isAnyEmpty(url,username)){
            throw new DynamicDataSourceException("primary数据源url或username或driver属性未填写");
        }
        if(StringUtils.isEmpty(driver)){
            if(url.contains("sqlserver")){
                driver = Constant.SQL_SERVER_DRIVER_CLASS_NAME;
            }else {
                driver = "com.mysql.jdbc.Driver";
            }
        }
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        if(StringUtils.isNotEmpty(password)) {
            dataSource.setPassword(password);
        }else{
            if(StringUtils.isEmpty(rsaPassword)){
                throw new DynamicDataSourceException("请配置数据库连接密码,password或rsa-password至少存在一个");
            }
            try {
                dataSource.setPassword(RSAUtil.decode(rsaPassword));
            } catch (Exception e) {
                throw new DynamicDataSourceException("数据库密码rsa-password解密异常");
            }
        }
        dataSource.setDriverClassName(driver);
        logger.info("begin to create completed");
        return dataSource;
    }

    /**
     * 设置启动类所在的 包名
     */
    private void setMainClassPackage() {
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
        if(MapUtils.isEmpty(beansWithAnnotation)){
            mainClassPackage = defaultMainClassPackage;
            return;
        }
        String classPackage = beansWithAnnotation.values().toArray()[0].getClass().getName();
        if(StringUtils.isEmpty(classPackage)){
            mainClassPackage = defaultMainClassPackage;
        } else{
            if(classPackage.lastIndexOf(".") < 0){
                mainClassPackage = defaultMainClassPackage;
            } else {
                mainClassPackage = classPackage.substring(0, classPackage.lastIndexOf("."));
            }
        }
    }

    /**
     * 注册并得到数据源
     *
     * @param dataSourceName
     * @param prop
     * @return
     */
    private DruidDataSource registerAndGetThisDataSource(String dataSourceName, Map<String, String> prop) {
        DruidDataSource dataSource = null;
        try {
            //注册数据源
            registerDataSources(dataSourceName + Constant.DATASOURCE, prop);
            //得到注册的数据源
            dataSource = getBean(dataSourceName + Constant.DATASOURCE);
        } catch (Exception e) {
            logger.info("Exception = {} ", e);
            throw new DynamicDataSourceException("创建" + dataSourceName + "数据源失败，请检查application配置文件中数据库配置信息是否配置正确");
        }
        logger.info("current DataSoruceName = {}", dataSourceName + "DataSource");
        return dataSource;
    }

    /**
     * jpa 环境配置
     *
     * @param dataSource
     * @param dataSourceName
     * @param prop
     */
    private void jpaConfigBuild(DataSource dataSource, String dataSourceName, Map<String, String> prop) {
        if(Objects.equals(prop.get(Constant.JPA_USE),Boolean.FALSE.toString())){
            logger.info("数据源 {} 没有使用 JPA",dataSourceName);
            return;
        }
        setDefaultJPAScanAndPackage(dataSourceName, prop);
        logger.info("Jpa {} properties begin ", dataSourceName);
        // 获取 jpaProperties
        JpaProperties jpaProperties = getBeanForMap(JpaProperties.class);
        HibernateProperties hibernateProperties = getBeanForMap(HibernateProperties.class);
        jpaProperties.setShowSql(isShowSql);
        logger.info("jpaProperties = {}", jpaProperties);
        //set  entityManagerFactoryPrimary4对象
        ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
        constructorArgumentValues.addGenericArgumentValue(dataSource);
        EntityManagerFactoryBuilder beanForMap = null;
        try {
            beanForMap = getBeanForMap(EntityManagerFactoryBuilder.class);
        } catch (Exception e) {
            logger.info("Exception = {} ", e);
            throw new DynamicDataSourceException("请保证配置文件中primaryDataSource或者primary至少存在一个，并且primaryDataSource下的值能够找到对应的datasource");
        }
        logger.info("EntityManagerFactoryBuilder = {}", beanForMap);
        constructorArgumentValues.addGenericArgumentValue(beanForMap);
        constructorArgumentValues.addGenericArgumentValue(jpaProperties);
        constructorArgumentValues.addGenericArgumentValue(dataSourceName);
        constructorArgumentValues.addGenericArgumentValue(hibernateProperties);
        constructorArgumentValues.addGenericArgumentValue(prop.get(Constant.JPA_MODEL_PACKAGE).split(","));
        logger.info("jpaModelPackage = {}", prop.get(Constant.JPA_MODEL_PACKAGE));
        //这里注意通过init设置的对象不能重复设置  所以方法内如果这个beanName对象已经存在 直接return
        LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean = null;
        try {
            setCosAndInitBean(Constant.ENTITY_MANAGER_FACTORY_PRIMARY4 + dataSourceName, EntityManagerFactoryBean.class, constructorArgumentValues);
            //获取刚才set进去的对象  注意获取到的要在前面加&
            localContainerEntityManagerFactoryBean = getBean("&" + Constant.ENTITY_MANAGER_FACTORY_PRIMARY4 + dataSourceName);
            logger.info("localContainerEntityManagerFactoryBean = {}", localContainerEntityManagerFactoryBean);
        } catch (Exception e) {
            logger.info("Exception", e);
            throw new DynamicDataSourceException(Constant.ENTITY_MANAGER_FACTORY_PRIMARY4 + dataSourceName + "创建失败，请检查配置文件是否完整");
        }
        try {
            // 构建 transactionManagerPrimary4
            ConstructorArgumentValues cxf = new ConstructorArgumentValues();
            cxf.addGenericArgumentValue(localContainerEntityManagerFactoryBean.getObject());

            setCosBean(Constant.TRANSACTION_MANAGER_PRIMARY4 + dataSourceName, JpaTransactionManager.class, dataSourceName, cxf);
            // 这里是为了 主数据源时，默认spring 去找 ioc中 transactionManager这个bean 但是这个bean 未经过我们配置，多数据源jpa事务会失效
            // 所以这里处理为 删除原spring自己提供的transactionManager  注入我们的主数据的配置，当然这时只能有一个事务管理器是primary的就是我们的transactionManager
            // 所以setCosBean的其他jpa事务管理器不能设置为primary
            if (primaryDataSource.contains(dataSourceName)) {
                removeBean(Constant.SPRING_JPA_TRANSACTIONMANAGER_BEAN_NAME);
                setCosBean(Constant.SPRING_JPA_TRANSACTIONMANAGER_BEAN_NAME, JpaTransactionManager.class, dataSourceName, cxf);
            }
            logger.info("this dataSource jpa is build completed");
        } catch (Exception e) {
            logger.info("Exception", e);
            throw new DynamicDataSourceException(Constant.TRANSACTION_MANAGER_PRIMARY4 + dataSourceName + "创建失败，请检查配置文件是否完整");
        }
    }

    public static void setDefaultJPAScanAndPackage(String dataSourceName, Map<String, String> prop) {
        serDefaultDriver(dataSourceName,prop);
        if (StringUtils.isEmpty(prop.get(Constant.JPA_MODEL_PACKAGE))) {
            logger.info("数据源 {}未配置 {} ,使用默认配置",dataSourceName, Constant.JPA_MODEL_PACKAGE);
            prop.put(Constant.JPA_MODEL_PACKAGE, mainClassPackage+".**.po");
        }
        if (StringUtils.isEmpty(prop.get(Constant.JPA_SCAN))) {
            logger.info("数据源 {}未配置 {} ,使用默认配置",dataSourceName, Constant.JPA_SCAN);
            prop.put(Constant.JPA_SCAN, mainClassPackage+".**." + dataSourceName + ".dao");
        }
    }

    public static void setDefaultMybatisScanAndPackage(String dataSourceName, Map<String, String> prop) {
        serDefaultDriver(dataSourceName,prop);
        if (StringUtils.isEmpty(prop.get(Constant.MYBATIS_MAPPER_PATH))) {
            logger.info("数据源 {}未配置 {} ,使用默认配置",dataSourceName, Constant.MYBATIS_MAPPER_PATH);
            if(Objects.equals(prop.get(Constant.DRIVER_CLASS_NAME), Constant.SQL_SERVER_DRIVER_CLASS_NAME)){
                prop.put(Constant.MYBATIS_MAPPER_PATH, "/mapper-sqlserver/**/*.xml");
            }else {
                prop.put(Constant.MYBATIS_MAPPER_PATH, "/mapper/**/*.xml");
            }

        }
        if (StringUtils.isEmpty(prop.get(Constant.MYBATIS_SCAN))) {
            logger.info("数据源 {}未配置 {} ,使用默认配置",dataSourceName, Constant.MYBATIS_SCAN);
            prop.put(Constant.MYBATIS_SCAN, mainClassPackage+".**." + dataSourceName + ".mapper");
        }
    }

    /**
     * 函数式声明Consumer
     */
    static Map<BeanDefinitionRegistry, Consumer> jpaConsumerMap = new ConcurrentHashMap<>();

    /**
     * 将consumer注入进这个方法
     *
     * @param beanDefinitionRegistry
     * @param f
     */
    public static void addJpaConsumer(BeanDefinitionRegistry beanDefinitionRegistry, Consumer f) {
        jpaConsumerMap.put(beanDefinitionRegistry, f);
    }


    public Map<String, Map<String, String>> getDatasource() {
        return datasource;
    }

    public static void setDatasource(Map<String, Map<String, String>> datasource) {
        JpaDataSourceConfiguration.datasource = datasource;
    }

    /**
     * 实现ApplicationContextAware接口的context注入函数, 将其存入静态变量.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        JpaDataSourceConfiguration.applicationContext = applicationContext;
    }

    /**
     * 取得存储在静态变量中的ApplicationContext.
     */
    public static ApplicationContext getApplicationContext() {
        checkApplicationContext();
        return applicationContext;
    }

    /**
     * 删除spring中管理的bean
     *
     * @param beanName
     */
    public static void removeBean(String beanName) {
        ApplicationContext ctx = getApplicationContext();
        DefaultListableBeanFactory acf = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
        acf.removeBeanDefinition(beanName);
    }

    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        checkApplicationContext();
        if (applicationContext.containsBean(name)) {
            return (T) applicationContext.getBean(name);
        }
        return null;
    }

    private static void checkApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("applicaitonContext未注入,请在applicationContext.xml中定义SpringContextUtil");
        }
    }

    public static <T> T getBeanForMap(Class<T> clazz) {
        checkApplicationContext();
        Map<String, T> beansOfType = applicationContext.getBeansOfType(clazz);

        return beansOfType.entrySet().stream().findFirst().get().getValue();
    }

    public static synchronized void setCosBean(String beanName, Class<?> clazz, String dataSourceName, ConstructorArgumentValues original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        //这里重要
        if (beanFactory.containsBean(beanName)) {
            return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(clazz);
        // 如果需要注册的bean 名字是transactionManager  直接设置为primary  替换掉本身的 transactionManager
        if (beanName.equals(Constant.SPRING_JPA_TRANSACTIONMANAGER_BEAN_NAME)) {
            definition.setPrimary(true);
        }
        // 当主数据源注册时 设置为Primary  这里如果为 transactionManager前缀时
        // 表示是jpa事务管理器，除了transactionManager能够设置为primary 其他一律设置为非主数据源
        String[] dataSourceSplitArray = primaryDataSource.split("\\.");
        if (dataSourceSplitArray[dataSourceSplitArray.length - 1].equals(dataSourceName) && !beanName.startsWith(Constant.SPRING_JPA_TRANSACTIONMANAGER_BEAN_NAME)) {
            definition.setPrimary(true);
        }
        //属性赋值
        definition.setConstructorArgumentValues(new ConstructorArgumentValues(original));
        //注册到spring上下文
        beanFactory.registerBeanDefinition(beanName, definition);
    }

    public static synchronized void setCosAndInitBean(String beanName, Class<?> clazz, ConstructorArgumentValues original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        if (beanFactory.containsBean(beanName)) {
            return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(clazz);
        definition.setFactoryMethodName("getInit");
        //属性赋值
        definition.setConstructorArgumentValues(new ConstructorArgumentValues(original));
        //注册到spring上下文
        beanFactory.registerBeanDefinition(beanName, definition);
    }

    public static void registerDataSources(String beanName, Map<String, String> config) {

        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        if (beanFactory.containsBean(beanName)) {
            return;
        }
        if (serDefaultDriver(beanName, config)) return;

        logger.info("register dataSource = {}", beanName + "DataSource");
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(DruidDataSource.class);
        // 此次循环是为了给不存在配置赋默认值
        defaultDataSourcePropMap.forEach((k, v) -> {
            if (!config.containsKey(k)) {
                config.put(k, defaultDataSourcePropMap.get(k));
            }
        });
        config.forEach((k, v) -> {
            if (k.contains("-")) {
                return;
            }
            if (Constant.PASSWORD.equals(k) && StringUtils.isNotEmpty(v)) {
                if (beanDefinitionBuilder.getBeanDefinition().getPropertyValues().get(Constant.PASSWORD) != null) {
                    return;
                }
                beanDefinitionBuilder.addPropertyValue(k, v);
                return;
            }
            if (Constant.RSA_PASSWORD.equals(k) && StringUtils.isNotEmpty(v)) {
                if (beanDefinitionBuilder.getBeanDefinition().getPropertyValues().get(Constant.PASSWORD) != null) {
                    return;
                }
                try {
                    beanDefinitionBuilder.addPropertyValue(Constant.PASSWORD, RSAUtil.decode(v));
                } catch (Exception e) {
                    logger.info("Exception = {} ", e);
                    throw new DynamicDataSourceException(beanName + "数据库密码rsa-password解密异常");
                }
                return;
            }
            beanDefinitionBuilder.addPropertyValue(k, v);
        });
        beanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
    }

    /**
     * 设置默认数据源驱动 根据 url中的串
     * @param beanName
     * @param config
     * @return
     */
    private static boolean serDefaultDriver(String beanName, Map<String, String> config) {
        if(StringUtils.isEmpty(config.get(Constant.DRIVER_CLASS_NAME))){
            logger.info("数据源 {}未配置 {} ,使用默认配置",beanName, Constant.DRIVER_CLASS_NAME);
            String url = config.get("url");
            if(StringUtils.isEmpty(url)){
                logger.error("数据源 {} 配置url 未填写",beanName);
                return true;
            }
            if(url.contains("sqlserver")){
                config.put(Constant.DRIVER_CLASS_NAME, Constant.SQL_SERVER_DRIVER_CLASS_NAME);
            }else {
                config.put(Constant.DRIVER_CLASS_NAME,"com.mysql.jdbc.Driver");
            }
        }
        return false;
    }

    /**
     * 初始化默认配置
     */
    static {
        defaultDataSourcePropMap.put("connectionProperties", "druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000");
        defaultDataSourcePropMap.put("testWhileIdle", "true");
        defaultDataSourcePropMap.put("maxActive", "10");
        defaultDataSourcePropMap.put("minIdle", "2");
        defaultDataSourcePropMap.put("initialSize", "2");
        defaultDataSourcePropMap.put("removeAbandoned", "true");
        defaultDataSourcePropMap.put("removeAbandonedTimeout", "280");
        defaultDataSourcePropMap.put("logAbandoned", "true");
        defaultDataSourcePropMap.put("validationQuery", "select 1");
    }
}