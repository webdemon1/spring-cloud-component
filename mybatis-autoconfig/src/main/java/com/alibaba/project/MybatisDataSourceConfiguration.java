package com.alibaba.project;


import com.alibaba.druid.pool.DruidDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 通过配置动态生成  mybatis 的实现核心, 实现了InitializingBean 做数据源配置，BeanPostProcessor 做初始化
 * 如果不实现BeanPostProcessor会出现E6DynamicDataSourceConfiguration不能加载的问题
 */
@Configuration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@ConfigurationProperties(prefix = "spring")
@Import(MybatisMapperScannerRegistrar.class)
public class MybatisDataSourceConfiguration implements InitializingBean, ApplicationContextAware, BeanPostProcessor {
    static Logger logger = LoggerFactory.getLogger(MybatisDataSourceConfiguration.class);
    public static Map<String, Map<String, String>> datasource = new HashMap<>();
    private static ApplicationContext applicationContext;
    /**
     * 默认数据源配置，如果自定义可以覆盖
     */
    private static Map<String, String> defaultDataSourcePropMap = new HashMap<>(9);

    @Value("${spring.jpa.show-sql:true}")
    public Boolean isShowSql;
    @Value("${main.class.package:com.alibaba.demon}")
    public String defaultMainClassPackage;

    public static String primaryDataSource;

    public static String mainClassPackage;

    @Value("${spring.primaryDataSource:spring.datasource.primary}")
    public void setPrimaryDataSource(String primaryDataSource) {
        MybatisDataSourceConfiguration.primaryDataSource = primaryDataSource;
    }
    /**
     * 核心方法
     *
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public void afterPropertiesSet() {
        setMainClassPackage();
        // 执行注册 mybatis
        mybatisConsumerMap.forEach((key, value) -> value.accept(key));
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
             * 构建mybatis环境
             */
            mybatisConfigBuild(dataSource, dataSourceName, prop);
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
        logger.info("current DataSourceName = {}", dataSourceName + "DataSource");
        return dataSource;
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
     * 自定义mybatis拦截器
     */
    @Autowired(required = false)
    List<Interceptor> interceptorList;

    /**
     * mybatis 环境配置
     *
     * @param dataSource
     * @param dataSourceName
     * @param prop
     */
    private void mybatisConfigBuild(DataSource dataSource, String dataSourceName, Map<String, String> prop) {
        //开始构建SqlSessionFactoryBean对象  这里是他的mapper.xml路径
        if(Objects.equals(prop.get(Constant.MYBATIS_USE),Boolean.FALSE.toString())){
            logger.info("数据源 {} 没有使用 Mybatis",dataSourceName);
            return;
        }
        // 设置默认 mybatis扫包
        setDefaultMybatisScanAndPackage(dataSourceName, prop);
        logger.info("Mybatis {} properties begin ", dataSourceName);
        String[] mappersPath_equipDataSource = prop.get(Constant.MYBATIS_MAPPER_PATH).split(",");
        logger.info("Mybatis Mapper path = {} ", prop.get(Constant.MYBATIS_MAPPER_PATH));

        // mybatis 事务配置
        try {
            ConstructorArgumentValues cxf3 = new ConstructorArgumentValues();
            cxf3.addGenericArgumentValue(dataSource);
            setCosBean(dataSourceName + "TransactionManager", DataSourceTransactionManager.class, cxf3);
        } catch (Exception e) {
            logger.info("Exception", e);
            throw new DynamicDataSourceException("配置mybatisTransactionManager失败，请检查" + dataSourceName + "数据源的配置");
        }
        //封装resource
        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
        List<Resource> resources = new LinkedList<>();
        for (String path : mappersPath_equipDataSource) {
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + path;
            Resource[] mapperLocations = new Resource[0];
            try {
                mapperLocations = pathMatchingResourcePatternResolver.getResources(packageSearchPath);
            } catch (IOException e) {
                logger.info("Exception", e);
                throw new DynamicDataSourceException("读取mybaits配置出错，请检查" + dataSourceName + "数据源的mybatis-mapper-path是否配置正确");
            }
            resources.addAll(Arrays.asList(mapperLocations));
        }
        if (resources.size() == 0) {
            //throw new E6DynamicDataSourceException(310,"请检查mybatis-mapper-path是否配置正确");
            logger.error("mapper.xml数量为0，请检查数据源的mybatis-mapper-path是否配置正确");
        }
        Map<String,Object> original = new HashMap<>(2);
        original.put("dataSource", dataSource);
        original.put("mapperLocations", resources.toArray((new Resource[0])));
        //设置SqlSessionFactoryBean
        Object sqlSessionFactoryBean = null;
        try {
            setBean(dataSourceName + "SqlSessionFactoryBean", SqlSessionFactoryBean.class, original);
            //得到构建好的sqlSessionFactoryBean 开始构建SqlSessionTemplate
            sqlSessionFactoryBean = getBean(dataSourceName + "SqlSessionFactoryBean");
            //增加mybatis自定义拦截器
            if (CollectionUtils.isNotEmpty(interceptorList)) {
                for (Interceptor interceptor : interceptorList) {
                    ((DefaultSqlSessionFactory) sqlSessionFactoryBean).getConfiguration().addInterceptor(interceptor);
                }
            }
            logger.info("sqlSessionFactoryBean = {} ", sqlSessionFactoryBean);
        } catch (Exception e) {
            logger.info("Exception = {} ", e);
            throw new DynamicDataSourceException(dataSourceName + "SqlSessionFactoryBean" + "创建失败，请检查配置文件和mapper.xml的配置是否正确");
        }
        ConstructorArgumentValues cxf2 = new ConstructorArgumentValues();
        cxf2.addGenericArgumentValue(sqlSessionFactoryBean);
        try {
            setCosBean(dataSourceName + "SqlSessionTemplate", SqlSessionTemplate.class, dataSourceName, cxf2);
            logger.info("this dataSource mybatis is build completed");
        } catch (Exception e) {
            logger.info("Exception", e);
            throw new DynamicDataSourceException(dataSourceName + "SqlSessionTemplate" + "创建失败，请检查配置文件和mapper.xml的配置是否正确");
        }
    }

    /**
     * 函数式声明Consumer
     */
    static Map<BeanDefinitionRegistry, Consumer> mybatisConsumerMap = new ConcurrentHashMap<>();

    /**
     * 将consumer注入进这个方法
     */
    public static void addMybatisConsumer(BeanDefinitionRegistry beanDefinitionRegistry, Consumer f) {
        mybatisConsumerMap.put(beanDefinitionRegistry, f);
    }


    public Map<String, Map<String, String>> getDatasource() {
        return datasource;
    }

    public static void setDatasource(Map<String, Map<String, String>> datasource) {
        MybatisDataSourceConfiguration.datasource = datasource;
    }

    /**
     * 实现ApplicationContextAware接口的context注入函数, 将其存入静态变量.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        MybatisDataSourceConfiguration.applicationContext = applicationContext;
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
            throw new IllegalStateException("applicationContext未注入,请在applicationContext.xml中定义SpringContextUtil");
        }
    }

    public static <T> T getBeanForMap(Class<T> clazz) {
        checkApplicationContext();
        Map<String, T> beansOfType = applicationContext.getBeansOfType(clazz);

        return beansOfType.entrySet().stream().findFirst().get().getValue();
    }

    /**
     * 同步方法注册bean到ApplicationContext中
     *
     * @param beanName
     * @param clazz
     * @param original bean的属性值
     */
    public static synchronized void setBean(String beanName, Class<?> clazz, Map<String, Object> original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        if (beanFactory.containsBean(beanName)) {
            return;
        }
        //BeanDefinition beanDefinition = new RootBeanDefinition(clazz);
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(clazz);
        //属性赋值
        definition.setPropertyValues(new MutablePropertyValues(original));
        //注册到spring上下文
        beanFactory.registerBeanDefinition(beanName, definition);
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

    public static synchronized void setCosBean(String beanName, Class<?> clazz, ConstructorArgumentValues original) {
        checkApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        //这里重要
        if (beanFactory.containsBean(beanName)) {
            return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        //类class
        definition.setBeanClass(clazz);
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

        logger.info("register dataSoruce = {}", beanName + "DataSource");
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