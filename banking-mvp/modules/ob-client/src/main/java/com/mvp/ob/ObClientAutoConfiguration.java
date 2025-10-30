package com.mvp.ob;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Лёгкая автоконфигурация: биндим свойства и сканируем пакет com.mvp.ob.
 */
@Configuration
@EnableConfigurationProperties(ObClientProperties.class)
@ComponentScan("com.mvp.ob")
public class ObClientAutoConfiguration { }
