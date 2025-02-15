package com.shelfsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /** Dedicated pool for invoice generation so slow PDF/Lambda calls never block request threads. */
    @Bean(name = "invoiceExecutor")
    public Executor invoiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("invoice-");
        executor.initialize();
        return executor;
    }
}
