# spring-cloud-component

apollo-client
封装的阿波罗客户端, 各个服务指定 appId.

spring-cloud-load-balance
基于 ribbon 的封装, 在进行 rpc 调用时, 为了方便程序调试, 指定服务的 ip 进行调用.如果本地没有找到对应的服务，则从可用的服务列表中随机取一个调用。