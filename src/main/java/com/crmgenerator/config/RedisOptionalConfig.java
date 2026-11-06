
package com.crmgenerator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Marker: Redis is optional. Cache falls back to in-memory if unavailable.
 */
@Configuration
@ConditionalOnProperty(name = "crm.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisOptionalConfig {
}
