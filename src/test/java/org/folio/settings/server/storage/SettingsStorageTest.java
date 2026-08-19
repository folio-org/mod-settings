package org.folio.settings.server.storage;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.UUID;
import org.folio.settings.server.TestContainersSupport;
import org.folio.settings.server.main.MainVerticle;
import org.folio.tlib.postgres.TenantPgPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SettingsStorageTest implements TestContainersSupport {

  @BeforeAll
  static void beforeAll() {
    TenantPgPool.setModule("mod-settings");
  }

  @Test
  void getLimitsIgnoredPermissions() {
    JsonArray perms = new JsonArray()
        .add("a")
        .add(1)
        .add("other.global.read.scope")
        .add("mod-settings.global.write.scope")
        .add("settings")
        .add("mod-settings.")
        .add("mod-settings.global")
        .add("mod-settings.global.")
        .add("mod-settings.global.read")
        .add("mod-settings.global.read.")
        .add("mod-settings.x.read.scope")
        .add("mod-settings.others.read.scope");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null), is(empty()));
  }

  @Test
  void getLimitsFromGlobal() {
    JsonArray perms = new JsonArray()
        .add("a")
        .add("mod-settings.global.write.s1")
        .add("mod-settings.global.read.s1.t1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope == \"s1.t1\" not userId = \"\")"));
  }

  @Test
  void getLimitsFromUsers() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.users.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope == \"s1\" and userId = \"\")"));
  }

  @Test
  void getLimitsFromOwn() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.owner.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null), is(empty()));
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        contains("(scope == \"s1\" and userId == \"" + myId + "\")"));
  }

  @Test
  void getLimitsMix1() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.owner.read.s1")
        .add("mod-settings.global.read.s2");
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        containsInAnyOrder(
            "(scope == \"s1\" and userId == \"" + myId + "\")",
            "(scope == \"s2\" not userId = \"\")"
        ));
  }

  @Test
  void getLimitsMix2() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.owner.read.s1")
        .add("mod-settings.global.read.s1");
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        contains(
            "(scope == \"s1\" not userId = \"\")",
            "(scope == \"s1\" and userId == \"" + myId + "\")"));
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains(
            "(scope == \"s1\" not userId = \"\")"));
  }

  @Test
  void getLimitsMix3() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.users.read.s1")
        .add("mod-settings.global.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("scope == \"s1\""));
  }

  @Test
  void migration(Vertx vertx, VertxTestContext vtc) {
    RestAssured.baseURI = "http://localhost:8081";
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    var pool = TenantPgPool.pool(vertx, "diku");

    vertx.deployVerticle(new MainVerticle())
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "0.0.0"))
    .compose(x -> pool.execute("INSERT INTO " + pool.getSchema() + ".settings "
                    + "(id, key, scope, value) VALUES ($1, $2, $3, $4)",
                    Tuple.tuple(List.of(UUID.randomUUID(),
                        "authority-archives-expiration", "authority-storage", "v"))))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage"))
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.1"))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage.manage"))
    .compose(x -> resetScope(pool))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage"))
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.2"))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage.manage"))
    .compose(x -> resetScope(pool))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage"))
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.3"))
    .compose(x -> assertAuthorityArchivesExpiration(pool, "authority-storage"))
    .onComplete(vtc.succeedingThenComplete());
  }

  Future<Void> assertAuthorityArchivesExpiration(TenantPgPool pool, String expectedScope) {
    return pool.execute("SELECT scope FROM " + pool.getSchema() + ".settings "
                        + "WHERE key='authority-archives-expiration'", Tuple.tuple())
        .map(rowSet -> {
          assertThat(rowSet.iterator().next().getString("scope"), is(expectedScope));
          return null;
        });
  }

  /**
   * Reset scope to authority-storage
   */
  Future<Void> resetScope(TenantPgPool pool) {
    var sql = "UPDATE " + pool.getSchema() + ".settings SET scope='authority-storage'";
    return pool.execute(List.of(sql));
  }
}
