package com.angel.flexbuddy.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ErrorAlertProperties.class)
public class AlertConfig {
}
