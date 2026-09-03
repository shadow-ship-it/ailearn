package com.oa.rag_ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步执行器配置。每个解析任务新开一个（虚拟）线程执行，避免阻塞上传主流程。
 */
@Configuration
public class AsyncConfig {

    @Bean("documentParseExecutor")
    public Executor documentParseExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
