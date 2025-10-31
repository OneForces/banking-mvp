package com.mvp.ob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Лёгкая автоконфигурация: биндим свойства и сканируем пакет com.mvp.ob.
 * Делает @Primary бин ObClientProperties, чтобы не было конфликта,
 * даже если где-то ещё включён ConfigurationPropertiesScan.
 */
@Configuration
@ComponentScan("com.mvp.ob")
public class ObClientAutoConfiguration {

    @Bean(name = "obClientProperties")
    @Primary
    @ConfigurationProperties(prefix = "app")
    public ObClientProperties obClientProperties() {
        return new ObClientProperties();
    }
}
