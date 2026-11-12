package com.jaymrli.teamknowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TeamKnowledgeProperties.class)
public class AppConfiguration {
}
