
package com.ctrip.framework.apollo.util;

import com.ctrip.framework.apollo.core.MetaDomainConsts;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.core.enums.EnvUtils;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


/**
 * 重写了 ConfigUtil 的 扫描 app.id 的日志
 */
public class ConfigUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);
    private int refreshInterval = 5;
    private TimeUnit refreshIntervalTimeUnit;
    private int connectTimeout;
    private int readTimeout;
    private String cluster;
    private int loadConfigQPS;
    private int longPollQPS;
    private long onErrorRetryInterval;
    private TimeUnit onErrorRetryIntervalTimeUnit;
    private long maxConfigCacheSize;
    private long configCacheExpireTime;
    private TimeUnit configCacheExpireTimeUnit;
    private long longPollingInitialDelayInMills;
    private boolean autoUpdateInjectedSpringProperties;

    public ConfigUtil() {
        this.refreshIntervalTimeUnit = TimeUnit.MINUTES;
        this.connectTimeout = 1000;
        this.readTimeout = 5000;
        this.loadConfigQPS = 2;
        this.longPollQPS = 2;
        this.onErrorRetryInterval = 1L;
        this.onErrorRetryIntervalTimeUnit = TimeUnit.SECONDS;
        this.maxConfigCacheSize = 500L;
        this.configCacheExpireTime = 1L;
        this.configCacheExpireTimeUnit = TimeUnit.MINUTES;
        this.longPollingInitialDelayInMills = 2000L;
        this.autoUpdateInjectedSpringProperties = true;
        this.initRefreshInterval();
        this.initConnectTimeout();
        this.initReadTimeout();
        this.initCluster();
        this.initQPS();
        this.initMaxConfigCacheSize();
        this.initLongPollingInitialDelayInMills();
        this.initAutoUpdateInjectedSpringProperties();
    }

    public String getAppId() {
        String appId = Foundation.app().getAppId();
        if (Strings.isNullOrEmpty(appId)) {
            // 这里修改了源码  如果没有设置appid 就拿这个appId 而这个appId下是一个空目录，这样就能够在引用了jar包的同时
            // 还可以不集成apollo环境
            appId = "e6yun-empty-config";
            logger.warn("检测到没有设置appId,设置为默认的appId  e6yun-empty-config  加载本地配置");
            logger.warn("app.id is not set, please make sure it is set in classpath:application.properties and /META-INF/app.properties, now apollo will only load public namespace configurations!");
        }

        return appId;
    }

    public String getDataCenter() {
        return Foundation.server().getDataCenter();
    }

    private void initCluster() {
        this.cluster = System.getProperty("apollo.cluster");
        if (Strings.isNullOrEmpty(this.cluster)) {
            this.cluster = this.getDataCenter();
        }

        if (Strings.isNullOrEmpty(this.cluster)) {
            this.cluster = "default";
        }

    }

    public String getCluster() {
        return this.cluster;
    }

    public Env getApolloEnv() {
        Env env = EnvUtils.transformEnv(Foundation.server().getEnvType());
        if (env == null) {
            String path = this.isOSWindows() ? "C:\\opt\\settings\\server.properties" : "/opt/settings/server.properties";
            String message = String.format("env is not set, please make sure it is set in %s!", path);
            logger.error(message);
            throw new ApolloConfigException(message);
        } else {
            return env;
        }
    }

    public String getLocalIp() {
        return Foundation.net().getHostAddress();
    }

    public String getMetaServerDomainName() {
        return MetaDomainConsts.getDomain(this.getApolloEnv());
    }

    private void initConnectTimeout() {
        String customizedConnectTimeout = System.getProperty("apollo.connectTimeout");
        if (!Strings.isNullOrEmpty(customizedConnectTimeout)) {
            try {
                this.connectTimeout = Integer.parseInt(customizedConnectTimeout);
            } catch (Throwable var3) {
                logger.error("Config for apollo.connectTimeout is invalid: {}", customizedConnectTimeout);
            }
        }

    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    private void initReadTimeout() {
        String customizedReadTimeout = System.getProperty("apollo.readTimeout");
        if (!Strings.isNullOrEmpty(customizedReadTimeout)) {
            try {
                this.readTimeout = Integer.parseInt(customizedReadTimeout);
            } catch (Throwable var3) {
                logger.error("Config for apollo.readTimeout is invalid: {}", customizedReadTimeout);
            }
        }

    }

    public int getReadTimeout() {
        return this.readTimeout;
    }

    private void initRefreshInterval() {
        String customizedRefreshInterval = System.getProperty("apollo.refreshInterval");
        if (!Strings.isNullOrEmpty(customizedRefreshInterval)) {
            try {
                this.refreshInterval = Integer.parseInt(customizedRefreshInterval);
            } catch (Throwable var3) {
                logger.error("Config for apollo.refreshInterval is invalid: {}", customizedRefreshInterval);
            }
        }

    }

    public int getRefreshInterval() {
        return this.refreshInterval;
    }

    public TimeUnit getRefreshIntervalTimeUnit() {
        return this.refreshIntervalTimeUnit;
    }

    private void initQPS() {
        String customizedLoadConfigQPS = System.getProperty("apollo.loadConfigQPS");
        if (!Strings.isNullOrEmpty(customizedLoadConfigQPS)) {
            try {
                this.loadConfigQPS = Integer.parseInt(customizedLoadConfigQPS);
            } catch (Throwable var5) {
                logger.error("Config for apollo.loadConfigQPS is invalid: {}", customizedLoadConfigQPS);
            }
        }

        String customizedLongPollQPS = System.getProperty("apollo.longPollQPS");
        if (!Strings.isNullOrEmpty(customizedLongPollQPS)) {
            try {
                this.longPollQPS = Integer.parseInt(customizedLongPollQPS);
            } catch (Throwable var4) {
                logger.error("Config for apollo.longPollQPS is invalid: {}", customizedLongPollQPS);
            }
        }

    }

    public int getLoadConfigQPS() {
        return this.loadConfigQPS;
    }

    public int getLongPollQPS() {
        return this.longPollQPS;
    }

    public long getOnErrorRetryInterval() {
        return this.onErrorRetryInterval;
    }

    public TimeUnit getOnErrorRetryIntervalTimeUnit() {
        return this.onErrorRetryIntervalTimeUnit;
    }

    public String getDefaultLocalCacheDir() {
        String cacheRoot = this.isOSWindows() ? "C:\\opt\\data\\%s" : "/opt/data/%s";
        return String.format(cacheRoot, this.getAppId());
    }

    public static void main(String[] args) {
        String cacheRoot = "C:\\opt\\data\\%s";
        System.out.println(String.format(cacheRoot, "ApolloNoAppIdPlaceHolder"));
    }

    public boolean isInLocalMode() {
        try {
            Env env = this.getApolloEnv();
            return env == Env.LOCAL;
        } catch (Throwable var2) {
            return false;
        }
    }

    public boolean isOSWindows() {
        String osName = System.getProperty("os.name");
        return !Strings.isNullOrEmpty(osName) && osName.startsWith("Windows");
    }

    private void initMaxConfigCacheSize() {
        String customizedConfigCacheSize = System.getProperty("apollo.configCacheSize");
        if (!Strings.isNullOrEmpty(customizedConfigCacheSize)) {
            try {
                this.maxConfigCacheSize = Long.parseLong(customizedConfigCacheSize);
            } catch (Throwable var3) {
                logger.error("Config for apollo.configCacheSize is invalid: {}", customizedConfigCacheSize);
            }
        }

    }

    public long getMaxConfigCacheSize() {
        return this.maxConfigCacheSize;
    }

    public long getConfigCacheExpireTime() {
        return this.configCacheExpireTime;
    }

    public TimeUnit getConfigCacheExpireTimeUnit() {
        return this.configCacheExpireTimeUnit;
    }

    private void initLongPollingInitialDelayInMills() {
        String customizedLongPollingInitialDelay = System.getProperty("apollo.longPollingInitialDelayInMills");
        if (!Strings.isNullOrEmpty(customizedLongPollingInitialDelay)) {
            try {
                this.longPollingInitialDelayInMills = Long.parseLong(customizedLongPollingInitialDelay);
            } catch (Throwable var3) {
                logger.error("Config for apollo.longPollingInitialDelayInMills is invalid: {}", customizedLongPollingInitialDelay);
            }
        }

    }

    public long getLongPollingInitialDelayInMills() {
        return this.longPollingInitialDelayInMills;
    }

    private void initAutoUpdateInjectedSpringProperties() {
        String enableAutoUpdate = System.getProperty("apollo.autoUpdateInjectedSpringProperties");
        if (Strings.isNullOrEmpty(enableAutoUpdate)) {
            enableAutoUpdate = Foundation.app().getProperty("apollo.autoUpdateInjectedSpringProperties", (String) null);
        }

        if (!Strings.isNullOrEmpty(enableAutoUpdate)) {
            this.autoUpdateInjectedSpringProperties = Boolean.parseBoolean(enableAutoUpdate.trim());
        }

    }

    public boolean isAutoUpdateInjectedSpringPropertiesEnabled() {
        return this.autoUpdateInjectedSpringProperties;
    }
}
