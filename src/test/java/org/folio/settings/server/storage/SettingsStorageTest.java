package org.folio.settings.server.storage;

import io.vertx.core.json.JsonArray;
import java.util.UUID;
import org.folio.settings.server.data.Entry;
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
  public void getLimitsFromGlobal() {
    JsonArray perms = new JsonArray()
        .add("a")
        .add("mod-settings.global.write.s1")
        .add("mod-settings.global.read.s1.t1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope == \"s1.t1\" not userId = \"\")"));
  }

  @Test
  public void getLimitsFromUsers() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.users.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("(scope == \"s1\" and userId = \"\")"));
  }

  @Test
  public void getLimitsFromOwn() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.owner.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null), is(empty()));
    UUID myId = UUID.randomUUID();
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, myId),
        contains("(scope == \"s1\" and userId == \"" + myId + "\")"));
  }

  @Test
  public void getLimitsMix1() {
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
  public void getLimitsMix2() {
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
  public void getLimitsMix3() {
    JsonArray perms = new JsonArray()
        .add("mod-settings.users.read.s1")
        .add("mod-settings.global.read.s1");
    assertThat(SettingsStorage.getCqlLimitPermissions(perms, null),
        contains("scope == \"s1\""));
  }

    @Test
    public void checkDesiredPermissionsGlobalWriteWithArbitrarySuffix() {
        String scope = "4f3a58cf-971f-4be5-9c97-2efddf07f4e9";
        Entry entry = new Entry();
        entry.setScope(scope);
        entry.setUserId(null);
        JsonArray perms = new JsonArray().add("mod-settings.global.write." + scope + ".anyTailToken");

        assertThat(SettingsStorage.checkDesiredPermissions("write", perms, entry, null), is(true));
    }

    @Test
    public void checkDesiredPermissionsDoesNotMatchScopePrefixCollision() {
        String scope = "abc";
        Entry entry = new Entry();
        entry.setScope(scope);
        entry.setUserId(null);
        JsonArray perms = new JsonArray().add("mod-settings.global.write." + scope + "x.tail");

        assertThat(SettingsStorage.checkDesiredPermissions("write", perms, entry, null), is(false));
    }

}
