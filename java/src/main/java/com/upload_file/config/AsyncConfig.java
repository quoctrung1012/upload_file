package com.upload_file.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean("taskExecutor")
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // Cau hinh thread pool cho chunk upload
    executor.setCorePoolSize(5);        // So thread toi thieu
    executor.setMaxPoolSize(20);        // So thread toi da
    executor.setQueueCapacity(100);     // Hang doi toi da
    executor.setThreadNamePrefix("ChunkUpload-");

    // Cau hinh xu ly khi shutdown
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);

    // Xu ly khi hang doi day
    executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

    executor.initialize();
    return executor;
  }

  @Bean("fileTaskExecutor")
  public TaskExecutor fileTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // Cau hinh rieng cho file operations
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("FileOperation-");

    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);

    executor.initialize();
    return executor;
  }
}