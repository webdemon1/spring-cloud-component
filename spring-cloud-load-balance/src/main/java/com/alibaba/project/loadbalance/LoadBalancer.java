package com.alibaba.project.loadbalance;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangbaojie@e6yun.com
 * @date 2020/9/30 15:36
 * 负载均衡器，这里主要是先判断是否有配置文件指定调用规则
 */
public class LoadBalancer<T extends Server> extends ZoneAwareLoadBalancer implements ILoadBalancer {

    static Logger logger = LoggerFactory.getLogger(LoadBalancer.class);
    /**
     * 存放每个client对应的MyLoadBalancer对象
     */
    static Map<String, ILoadBalancer> clientLoadBalancerMap = new ConcurrentHashMap();
    /**
     * 存放每个client 的  当前可用服务集合
     */
    static ConcurrentHashMap<String, Set<Server>> clientServerMap = new ConcurrentHashMap();

    static ConfigFileLoader configFileLoader;

    public LoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping, ServerList<T> serverList, ServerListFilter<T> filter, ServerListUpdater serverListUpdater, ConfigFileLoader configFileLoader) {
        super(clientConfig, rule, ping, serverList, filter, serverListUpdater);
        clientLoadBalancerMap.put(clientConfig.getClientName(), this);
        LoadBalancer.configFileLoader = configFileLoader;
    }

    /**
     * 在父类DynamicServerListLoadBalancer中注册了一个定时器回调方法，会定时调用这个方法
     */
    @Override
    public void updateListOfServers() {
        String clientName = this.getClientConfig().getClientName();
        List<Server> servers = new ArrayList();
        if (super.getServerListImpl() != null) {
            //这里可能再次扫描进来还是两个，即使已经排除过了
            servers = super.getServerListImpl().getUpdatedListOfServers();
            //获取上一次的可用服务集合
            Set<Server> lastServerSet = clientServerMap.get(clientName);
            //当前的可用服务集合
            Set<Server> newServerSet = new HashSet<>();
            newServerSet.addAll(servers);
            if (lastServerSet == null) {
                clientServerMap.put(clientName, newServerSet);
                logger.info("[{}]首次从eureka同步服务:{}", clientName, servers);
            } else {
                if (!newServerSet.equals(lastServerSet)) {
                    logger.info("[{}]服务集变化:{}-->{}", clientName, lastServerSet, newServerSet);
                    clientServerMap.put(clientName, newServerSet);
                }
            }
            if (servers.size() < 1) {
                logger.warn("[{}]无可用服务", clientName);
            }
            if (super.getFilter() != null) {
                servers = super.getFilter().getFilteredListOfServers((List) servers);
                if (servers.size() != newServerSet.size()) {
                    logger.info("过滤生效，过滤后:{}", servers);
                }
            }
        }
        this.updateAllServerList(servers);
    }

    @Override
    public Server chooseServer(Object key) {
        String serviceName = this.getName();
        List<Server> serverList = configFileLoader.getConfigServer(serviceName);
        //如果有包含IP的，则不通过eureka返回的清单中供选择，直接调用
        if (serverList != null) {
            Optional<Server> serverOptional = serverList.stream().filter(server -> server.getPort() > 0).findFirst();
            if (serverOptional.isPresent()) {
                logger.info("[{}],chooseServer(skip eureka)通过本地配置直接访问:{}", serviceName, serverOptional.get().getId());
                return serverOptional.get();
            }
        }
        logger.info("[{}],chooseServerAll={}", serviceName, super.getAllServers());
        Server server = super.chooseServer(key);
        logger.info("[{}],chooseServer,选中={}", serviceName, server);
        return server;
    }


    @Override
    public void markServerDown(Server server) {
        logger.warn("[{}],markServerDown,server={}", this.getName(), server);
        super.markServerDown(server);
    }

    @Override
    public void markServerDown(String id) {
        logger.warn("[{}],markServerDown,id={}", this.getName(), id);
        super.markServerDown(id);
    }

    @Override
    public void addServer(Server newServer) {
        logger.warn("[{}],addServer:newServer={}", this.getName(), newServer);
        super.addServer(newServer);
    }

    @Override
    public void addServers(List<Server> newServers) {
        logger.warn("[{}],addServers:newServers={}", this.getName(), newServers);
        super.addServers(newServers);
    }

    @Override
    public void setPingInterval(int pingIntervalSeconds) {
        logger.warn("setPingInterval:pingIntervalSeconds={}", pingIntervalSeconds);
        if (pingIntervalSeconds >= 1) {
            this.pingIntervalSeconds = pingIntervalSeconds;
            if (logger.isDebugEnabled()) {
                logger.debug("LoadBalancer [{}]:  pingIntervalSeconds set to {}", this.name, this.pingIntervalSeconds);
            }
        }
    }

    @Override
    public void setPing(IPing ping) {
        super.setPing(ping);
    }
}
