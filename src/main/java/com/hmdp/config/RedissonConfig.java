package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * redisson配置
 *
 * @author CHEN
 * @date 2022/10/10
 */
@Configuration
public class RedissonConfig {
    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private String port;

    // **为密码占位符设置默认值**
    @Value("${spring.redis.password:}") // 如果配置文件中没有此项，则默认值为一个空字符串
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        String redisAddress = "redis://" + host + ":" + port;
        config.useSingleServer().setAddress(redisAddress);

        // **根据密码是否为空来决定是否设置密码**
        // 这样可以避免在无密码时调用 setPassword() 方法
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }

        // 创建并返回 RedissonClient 实例
        return Redisson.create(config);
    }
}
