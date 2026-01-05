package org.folio.settings.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import org.folio.tlib.postgres.TenantPgPool;

public class VersionStorage {

  private final TenantPgPool pool;

  private final String versionTable;

  /**
   * Database storage for locale settings for a tenant.
   */
  public VersionStorage(Vertx vertx, String tenant) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.versionTable = pool.getSchema() + ".module_version";
  }

  /**
   * Create the module_version database table, and set the version.
   */
  public Future<Void> init() {
    return pool.execute(List.of(
        """
        CREATE TABLE IF NOT EXISTS %s
        (version text NOT NULL)
        """.formatted(versionTable),

        """
        DO $$
          BEGIN
            INSERT INTO %s VALUES ('0.0.0') ON CONFLICT DO NOTHING;
          EXCEPTION WHEN SQLSTATE 'P0001' THEN NULL;
          END
        $$
        """.formatted(versionTable),

        """
        CREATE OR REPLACE FUNCTION %s.version_singleton()
          RETURNS TRIGGER AS $$
          BEGIN
            IF (TG_OP = 'DELETE') THEN
              RAISE EXCEPTION 'Cannot delete version record.';
            ELSIF (TG_OP = 'INSERT') THEN
              RAISE EXCEPTION 'Cannot insert second version record.';
            END IF;
            RETURN NEW;
          END;
          $$ language plpgsql;
        """.formatted(pool.getSchema()),

        """
        CREATE OR REPLACE TRIGGER version_singleton BEFORE DELETE OR INSERT ON %s
          EXECUTE FUNCTION %s.version_singleton()
        """.formatted(versionTable, pool.getSchema())
        ));
  }

  /**
   * Get version.
   */
  public Future<String> getVersion() {
    return pool.query("SELECT version FROM " + versionTable)
        .execute()
        .map(rowSet -> rowSet.iterator().next().getString("version"));
  }

  /**
   * Update version.
   */
  public Future<Void> setVersion(String version) {
    return pool.preparedQuery("UPDATE " + versionTable + " SET version = $1")
        .execute(Tuple.of(version))
        .mapEmpty();
  }
}
