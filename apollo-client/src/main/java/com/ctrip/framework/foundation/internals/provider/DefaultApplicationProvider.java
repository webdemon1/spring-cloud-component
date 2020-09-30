package com.ctrip.framework.foundation.internals.provider;

import com.ctrip.framework.foundation.internals.Utils;
import com.ctrip.framework.foundation.internals.io.BOMInputStream;
import com.ctrip.framework.foundation.spi.provider.ApplicationProvider;
import com.ctrip.framework.foundation.spi.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;


/**
 * 重写了DefaultApplicationProvider 的 获取 app.id的位置 由META-INF/app.properties 迁移至 application.properties
 */
public class DefaultApplicationProvider implements ApplicationProvider {
    private static final Logger logger = LoggerFactory.getLogger(DefaultApplicationProvider.class);
    public static final String APP_PROPERTIES_CLASSPATH = "application.properties";
    public static String inLocation = "/META-INF/app.properties";
    private Properties m_appProperties = new Properties();
    private String m_appId;

    public DefaultApplicationProvider() {
    }

    public void initialize() {
        // 先从 META-INF中获取app.id
        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("/META-INF/app.properties");
            if (in == null) {
                in = DefaultApplicationProvider.class.getResourceAsStream("/META-INF/app.properties");
            }

            if (in == null) {
                inLocation = APP_PROPERTIES_CLASSPATH;
                in = Thread.currentThread().getContextClassLoader().getResourceAsStream(APP_PROPERTIES_CLASSPATH);
            }

            if (in == null) {
                inLocation = APP_PROPERTIES_CLASSPATH;
                in = DefaultApplicationProvider.class.getResourceAsStream(APP_PROPERTIES_CLASSPATH);
            }

            if (in == null) {
                logger.warn("{} not found from classpath!", APP_PROPERTIES_CLASSPATH + " and /META-INF/app.properties");
            }

            this.initialize(in);
        } catch (Throwable var2) {
            logger.error("Initialize DefaultApplicationProvider failed.", var2);
        }

    }

    public void initialize(InputStream in) {
        try {
            if (in != null) {
                try {
                    this.m_appProperties.load(new InputStreamReader(new BOMInputStream(in), StandardCharsets.UTF_8));
                } finally {
                    in.close();
                }
            }

            this.initAppId();
        } catch (Throwable var6) {
            logger.error("Initialize DefaultApplicationProvider failed.", var6);
        }

    }

    public String getAppId() {
        return this.m_appId;
    }

    public boolean isAppIdSet() {
        return !Utils.isBlank(this.m_appId);
    }

    public String getProperty(String name, String defaultValue) {
        String val;
        if ("app.id".equals(name)) {
            val = this.getAppId();
            return val == null ? defaultValue : val;
        } else {
            val = this.m_appProperties.getProperty(name, defaultValue);
            return val == null ? defaultValue : val;
        }
    }

    public Class<? extends Provider> getType() {
        return ApplicationProvider.class;
    }

    private void initAppId() {
        logger.info("检测到使用common-apollo-client 从配置中获取 app.id :{}", this.m_appId);
        this.m_appId = System.getProperty("app.id");
        if (!Utils.isBlank(this.m_appId)) {
            this.m_appId = this.m_appId.trim();
            logger.info("App ID is set to {} by app.id property from System Property", this.m_appId);
        } else {
            this.m_appId = this.m_appProperties.getProperty("app.id");
            if (!Utils.isBlank(this.m_appId)) {
                this.m_appId = this.m_appId.trim();
                logger.info("common-apollo-client检测到 app.id = {} 从 {} ", this.m_appId, inLocation);
                logger.info("App ID is set to {} by app.id property from {} ", this.m_appId, inLocation);
            } else {
                this.m_appId = null;
                logger.warn("common-apollo-client没有从 {} OR {} 检测到 app.id,请检查配置 ", "application.properties", "/META-INF/app.properties");
            }
        }
    }

    public String toString() {
        return "appId [" + this.getAppId() + "] properties: " + this.m_appProperties + " (DefaultApplicationProvider)";
    }
}
