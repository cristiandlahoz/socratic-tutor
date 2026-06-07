package com.wornux.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
class DocumentDatabaseSchemaIT {

  private static final DockerImageName PGVECTOR_IMAGE =
      DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(PGVECTOR_IMAGE)
          .withDatabaseName("socratic-tutor")
          .withUsername("postgres")
          .withPassword("postgres");

  @Test
  void flywayCreatesDocumentTablesAndAcceptsPgvectorEmbeddings() throws Exception {
    migrate();

    try (var connection = DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          insert into vector_store (content, metadata, embedding)
          select 'binary search', '{"clientId":"test"}'::json,
              ('[' || array_to_string(array_fill(0.01, ARRAY[1024]), ',') || ']')::vector
          """);

      assertThat(count(statement, "ingested_document")).isZero();
      assertThat(count(statement, "document_segment")).isZero();
      assertThat(count(statement, "document_ingestion_job")).isZero();
      assertThat(count(statement, "vector_store")).isEqualTo(1);
      assertThat(vectorDimensions(statement)).isEqualTo(1024);
    }
  }

  private void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  private int count(java.sql.Statement statement, String tableName) throws Exception {
    try (var resultSet = statement.executeQuery("select count(*) from %s".formatted(tableName))) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private int vectorDimensions(java.sql.Statement statement) throws Exception {
    try (var resultSet = statement.executeQuery("select vector_dims(embedding) from vector_store limit 1")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }
}
