package com.beautyboy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private int accessExpMinutes;
    private int refreshExpDays;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getAccessExpMinutes() {
        return accessExpMinutes;
    }

    public void setAccessExpMinutes(int accessExpMinutes) {
        this.accessExpMinutes = accessExpMinutes;
    }

    public int getRefreshExpDays() {
        return refreshExpDays;
    }

    public void setRefreshExpDays(int refreshExpDays) {
        this.refreshExpDays = refreshExpDays;
    }
}
