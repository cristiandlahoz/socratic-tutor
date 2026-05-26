package com.wornux.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  @Bean(initMethod = "migrate")
  @ConditionalOnMissingBean(Flyway.class)
  Flyway flyway(DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
  }
}
