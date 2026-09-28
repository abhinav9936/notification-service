package com.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * One bounded worker pool per channel -- this is the "bounded worker pools" requirement, and
 * separating by channel means a slow/backed-up channel (e.g. a flaky SMS provider) can't starve
 * dispatch of the others.
 */
@Configuration
public class AsyncExecutorConfig {

    @Bean("emailExecutor")
    public ThreadPoolTaskExecutor emailExecutor() {
        return channelExecutor("email-worker-");
    }

    @Bean("smsExecutor")
    public ThreadPoolTaskExecutor smsExecutor() {
        return channelExecutor("sms-worker-");
    }

    @Bean("pushExecutor")
    public ThreadPoolTaskExecutor pushExecutor() {
        return channelExecutor("push-worker-");
    }

    @Bean("inAppExecutor")
    public ThreadPoolTaskExecutor inAppExecutor() {
        return channelExecutor("inapp-worker-");
    }

    private ThreadPoolTaskExecutor channelExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
