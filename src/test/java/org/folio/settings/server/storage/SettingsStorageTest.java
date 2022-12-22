package org.folio.settings.server.storage;

import io.vertx.core.json.JsonArray;
import java.util.UUID;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class SettingsStorageTest {

  @Test
  public void getLimitsIgnoredPermissions() {
    JsonArray perms = new JsonArray()
        .add("a")
        .add(1)
        .add("other.global.read.scope")
        .add("settings.global.write.scope")
        .add("settings.global.read.scope.x")
        .add("settings.others.read.scope");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null), is(empty()));
  }

  @Test
  public void getLimitsFromGlobal() {
    JsonArray perms = new JsonArray()
        .add("a")
        .add("settings.global.write.s1")
        .add("settings.global.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope = \"s1\" and userId <> \"\")"));
  }

  @Test
  public void getLimitsFromUsers() {
    JsonArray perms = new JsonArray()
        .add("settings.users.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope = \"s1\" and userId = \"\")"));
  }

  @Test
  public void getLimitsFromOwn() {
    JsonArray perms = new JsonArray()
        .add("settings.owner.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null), is(empty()));
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        contains("(scope = \"s1\" and userId = \"" + myId + "\")"));
  }

  @Test
  public void getLimitsMix1() {
    JsonArray perms = new JsonArray()
        .add("settings.owner.read.s1")
        .add("settings.global.read.s2");
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        containsInAnyOrder(
            "(scope = \"s1\" and userId = \"" + myId + "\")",
            "(scope = \"s2\" and userId <> \"\")"
        ));
  }

  @Test
  public void getLimitsMix2() {
    JsonArray perms = new JsonArray()
        .add("settings.owner.read.s1")
        .add("settings.global.read.s1");
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        contains(
            "(scope = \"s1\" and (userId <> \"\" or userId = \"" + myId + "\"))"));
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains(
            "(scope = \"s1\" and userId <> \"\")"));
  }

}
