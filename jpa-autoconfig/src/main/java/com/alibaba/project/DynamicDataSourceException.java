package com.alibaba.project;


public class DynamicDataSourceException extends RuntimeException {
    /**
     * 自定义构造器，只保留一个，让其必须输入错误码及内容
     */
    public DynamicDataSourceException(String msg) {
        super(msg);
    }
}
