package org.folio.settings.server;

import org.folio.tlib.postgres.testing.TenantPgPoolContainer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
@Testcontainers
public interface TestContainersSupport {

  @Container
  PostgreSQLContainer<?> postgresContainer = TenantPgPoolContainer.create();

}
