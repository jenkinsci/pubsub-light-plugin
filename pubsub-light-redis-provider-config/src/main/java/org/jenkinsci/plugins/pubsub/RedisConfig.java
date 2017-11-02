package org.jenkinsci.plugins.pubsub;

public interface RedisConfig {
    String getRedisHost();

    int getRedisPort();

    boolean isRedisSSL();
}
