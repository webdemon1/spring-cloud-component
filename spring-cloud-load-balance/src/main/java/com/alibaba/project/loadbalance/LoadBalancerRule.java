package com.alibaba.project.loadbalance;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.RandomRule;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * @author wangbaojie@e6yun.com
 * @date 2020/9/30 15:38
 */
public class LoadBalancerRule extends RandomRule {

    static Logger logger = LoggerFactory.getLogger(LoadBalancerRule.class);
    static ConfigFileLoader configFileLoader;

    public LoadBalancerRule() {
    }

    public void init(ConfigFileLoader configFileLoader) {
        LoadBalancerRule.configFileLoader = configFileLoader;
    }

    @Override
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        if (!BaseLoadBalancer.class.isInstance(lb)) {
            return super.choose(lb, key);
        }
        BaseLoadBalancer baseLoadBalancer = (BaseLoadBalancer) lb;
        String serviceName = baseLoadBalancer.getName();
        final List<Server> configServerList = configFileLoader.getConfigServer(serviceName);
        //如果没有配置，则调用随机获取
        if (configServerList == null || configServerList.isEmpty()) {
            return super.choose(lb, key);
        }
        Server server = null;
        while (server == null) {

            if (Thread.interrupted()) {
                return null;
            }
            List<Server> upList = lb.getReachableServers();
            Optional<Server> serverOptional = upList.stream().filter(server1 -> {
                for (Server s : configServerList) {
                    //这里只检查IP匹配，因为端口如果配置了，那么在之前执行的E6LoadBalancer里已经直接调用了
                    if (server1.getHost().equals(s.getHost())) {
                        return true;
                    }
                }
                return false;
            }).findFirst();
            if (serverOptional.isPresent()) {
                server = serverOptional.get();
                logger.info("[{}]找到服务:{}，当前:{}", serviceName, server.getId(), upList);
                return server;
            }
            logger.error("[{}]没有找到期望的服务，期望:{}，当前:{}", serviceName, configServerList, upList);
            throw new RuntimeException("[" + serviceName + "]没有找到期望的服务，期望:" + configServerList.toString() + "，当前:" + upList.toString());
        }
        return server;
    }
}
