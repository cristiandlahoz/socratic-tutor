package com.wornux.chat.profile;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringJUnitConfig(StudentProfilePersistenceIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class StudentProfilePersistenceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("pgvector/pgvector:pg18");

  @Autowired private StudentProfileService studentProfileService;

  @Test
  void applyTurnSignals_persists_profile_topics_and_misconceptions() {
    UUID clientId = UUID.randomUUID();
    var update =
        new TurnProfileUpdate(
            null,
            UUID.randomUUID(),
            List.of(TopicKey.LOOPS),
            List.of(
                new TurnProfileUpdate.LevelSignal(
                    TopicKey.LOOPS, TurnProfileUpdate.SignalDirection.DOWN, "confusion")),
            List.of(
                new TurnProfileUpdate.MisconceptionObservation(
                    TopicKey.LOOPS,
                    "counter_vs_accumulator",
                    "Confunde contador y acumulador",
                    new BigDecimal("0.800"))),
            "es",
            HelpMode.GUIDED,
            true,
            new BigDecimal("-0.060"),
            List.of(new TurnProfileUpdate.ToolEvidence("traceCProgram", true, "steps=4")),
            Map.of("topicsDetected", List.of("LOOPS")));

    studentProfileService.applyTurnSignals(clientId, update);
    var snapshot = studentProfileService.load(clientId);

    assertThat(snapshot.topWeakTopics()).contains(TopicKey.LOOPS);
    assertThat(snapshot.activeMisconceptions()).contains("counter_vs_accumulator");
    assertThat(snapshot.needsConcreteExamples()).isTrue();
    assertThat(snapshot.profileVersion()).isGreaterThanOrEqualTo(1L);
  }

  @TestConfiguration
  @Import(StudentProfileService.class)
  @EnableJpaRepositories(
      basePackageClasses = {
        StudentProfileJpaRepository.class,
        StudentTopicMasteryJpaRepository.class,
        StudentMisconceptionJpaRepository.class,
        StudentProfileSignalJpaRepository.class
      })
  @EnableTransactionManagement
  static class TestConfig {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource) {
      return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }

    @Bean
    DataSource dataSource() {
      var dataSource = new SimpleDriverDataSource();
      dataSource.setDriverClass(org.postgresql.Driver.class);
      dataSource.setUrl(POSTGRES.getJdbcUrl());
      dataSource.setUsername(POSTGRES.getUsername());
      dataSource.setPassword(POSTGRES.getPassword());
      return dataSource;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource dataSource, Flyway flyway) {
      var factoryBean = new LocalContainerEntityManagerFactoryBean();
      factoryBean.setDataSource(dataSource);
      factoryBean.setPackagesToScan(StudentProfileEntity.class.getPackageName());
      factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factoryBean.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto", "none",
              "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"));
      return factoryBean;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    ProfileProperties profileProperties() {
      return new ProfileProperties();
    }

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
