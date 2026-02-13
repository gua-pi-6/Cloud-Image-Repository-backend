package com.chen.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CaffeineManager {

    // 构建本地缓存
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    // 从本地缓存当中获取值
    public String getFromLocalCache(String key){
        return LOCAL_CACHE.getIfPresent(key);
    }

    // 向本地缓存当中写入值
    public void putToLocalCache(String key, String value){
        LOCAL_CACHE.put(key, value);
    }




}
