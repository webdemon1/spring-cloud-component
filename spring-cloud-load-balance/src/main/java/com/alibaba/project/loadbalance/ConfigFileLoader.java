package com.alibaba.project.loadbalance;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import com.netflix.loadbalancer.Server;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangbaojie@e6yun.com
 * @date 2020/9/30 15:36
 * 配置文件加载，参考apollo的配置文件加载类com.ctrip.framework.foundation.internals.provider.DefaultServerProvider
 **/
public class ConfigFileLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigFileLoader.class);
    private static final String SERVER_PROPERTIES_LINUX = "/opt/settings/e6RpcConfig.properties";
    private static final String SERVER_PROPERTIES_WINDOWS = "C:/opt/settings/e6RpcConfig.properties";

    private Properties configProperties = new Properties();
    private Map<String, List<Server>> configServersMap = new ConcurrentHashMap<>();

    public void initialize() {
        try {
            String path = isOSWindows() ? SERVER_PROPERTIES_WINDOWS : SERVER_PROPERTIES_LINUX;
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                logger.info("Loading {}", file.getAbsolutePath());
                FileInputStream fis = new FileInputStream(file);
                try {
                    configProperties.load(new InputStreamReader(fis, StandardCharsets.UTF_8));
                    //把配置解析为 Map，方便调用
                    configProperties.stringPropertyNames().forEach(name -> {
                        String desireIpPorts = (String) configProperties.get(name);
                        //如果没有配置，则调用随机获取
                        if (StringUtils.isNotBlank(desireIpPorts)) {
                            String[] ipAndPorts = desireIpPorts.split(",");
                            Server server = null;
                            for (String ipAndPort : ipAndPorts) {
                                if (ipAndPort.indexOf(":") > 0) {
                                    String[] ipPort = ipAndPort.split(":");
                                    server = new Server(ipPort[0], Integer.parseInt(ipPort[1]));
                                } else {
                                    server = new Server(ipAndPort, -1);
                                }
                                List<Server> serverList = configServersMap.get(name);
                                if (serverList == null) {
                                    serverList = new ArrayList<>();
                                    configServersMap.put(name, serverList);
                                }
                                serverList.add(server);
                            }
                        }
                    });
                    logger.info("配置文件{}解析完毕:{}", path, JSONObject.toJSON(configServersMap));
                } catch (Exception ex) {
                    logger.error("解析配置文件{}发生错误", path, ex);
                } finally {
                    fis.close();
                }
                return;
            }
        } catch (Throwable ex) {
            logger.error("初始化本地配置发生失败 failed.", ex);
        }
    }


    /**
     * 获取属性值
     *
     * @param name
     * @return
     */
    public List<Server> getConfigServer(String name) {
        return configServersMap.get(name);
    }

    public static boolean isOSWindows() {
        String osName = System.getProperty("os.name");
        if (isBlank(osName)) {
            return false;
        }
        return osName.startsWith("Windows");
    }

    public static boolean isBlank(String str) {
        return Strings.nullToEmpty(str).trim().isEmpty();
    }
}
