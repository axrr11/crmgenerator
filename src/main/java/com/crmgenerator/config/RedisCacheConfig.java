package com.crmgenerator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Value("${crm.cache.search-ttl-seconds:120}")
    private long searchTtlSeconds;

    @Value("${crm.cache.enabled:true}")
    private boolean cacheEnabled;

    /** Use Redis only when crm.cache.redis=true; default is safe in-memory. */
    @Value("${crm.cache.redis:false}")
    private boolean useRedis;

    @Bean
    CacheManager cacheManager(org.springframework.beans.factory.ObjectProvider<RedisConnectionFactory> redis) {
        if (!cacheEnabled) {
            return new ConcurrentMapCacheManager("moduleSearch", "moduleCount", "lookups");
        }
        RedisConnectionFactory factory = redis.getIfAvailable();
        if (!useRedis || factory == null) {
            return new ConcurrentMapCacheManager("moduleSearch", "moduleCount", "lookups");
        }
        try {
            factory.getConnection().close();
            GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(searchTtlSeconds))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
                    .disableCachingNullValues();
            Map<String, RedisCacheConfiguration> caches = new HashMap<>();
            caches.put("moduleSearch", defaults);
            caches.put("moduleCount", defaults);
            caches.put("lookups", defaults.entryTtl(Duration.ofSeconds(Math.max(searchTtlSeconds, 180))));
            return RedisCacheManager.builder(factory)
                    .cacheDefaults(defaults)
                    .withInitialCacheConfigurations(caches)
                    .build();
        } catch (Exception e) {
            return new ConcurrentMapCacheManager("moduleSearch", "moduleCount", "lookups");
        }
    }
}
