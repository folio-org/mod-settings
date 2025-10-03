package org.folio.settings.server.data;

import static org.junit.Assert.assertThrows;

import java.util.UUID;
import org.junit.Test;

public class EntryTest {
  @Test
  public void testValidate() {
    Entry e = new Entry();
    e.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    e.setScope("a");
    e.setKey("b");
    e.setValue("x", "y");
    e.validate();
  }

  @Test
  public void testMissingId() {
    Entry e = new Entry();
    e.setScope("a");
    e.setKey("b");
    e.setValue("x", "y");
    assertThrows(IllegalArgumentException.class, e::validate).getMessage().concat("id");
  }

  @Test
  public void testMissingScope() {
    Entry e = new Entry();
    e.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    e.setKey("b");
    e.setValue("x", "y");
    assertThrows(IllegalArgumentException.class, e::validate).getMessage().concat("scope");
  }

  @Test
  public void testMissingKey() {
    Entry e = new Entry();
    e.setScope("a");
    e.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    e.setValue("x", "y");
    assertThrows(IllegalArgumentException.class, e::validate).getMessage().concat("key");
  }

}
