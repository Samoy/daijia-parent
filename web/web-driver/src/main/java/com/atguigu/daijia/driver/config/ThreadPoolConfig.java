package com.atguigu.daijia.driver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                processors + 1,
                processors + 1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3),
                Executors.defaultThreadFactory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
        );
    }
}
