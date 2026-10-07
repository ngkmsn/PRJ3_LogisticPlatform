package com.logistics.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
class RedisSmokeIntegrationTest {

    @Autowired(required = false)
    private RedisConnectionFactory connectionFactory;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testRedisBasicSetAndGetOperation() {
        assertNotNull(connectionFactory, "RedisConnectionFactory must be configured");
        assertNotNull(stringRedisTemplate, "StringRedisTemplate must be configured");
        assertNotNull(redisTemplate, "RedisTemplate must be configured");

        boolean isRedisAvailable = false;
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            isRedisAvailable = "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            // Redis server is not reachable in current environment
        }

        assumeTrue(isRedisAvailable, "Skipping live Redis set/get test because Redis server is not reachable");

        // Execute basic SET operation
        String testKey = "smoke:test:key";
        String testValue = "LogisticsRedisValue123";
        stringRedisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(30));

        // Execute basic GET operation
        String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
        assertEquals(testValue, retrievedValue, "Retrieved value from Redis must match the stored value");

        // Cleanup
        Boolean deleted = stringRedisTemplate.delete(testKey);
        assertTrue(deleted != null && deleted, "Test key must be cleaned up");
    }
}
