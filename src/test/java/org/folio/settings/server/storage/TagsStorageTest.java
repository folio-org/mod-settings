package org.folio.settings.server.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TagsStorageTest {

  @ParameterizedTest
  @CsvSource({
    "true, true",
    "TRUE, true",
    "True, true",
    "false, false",
    "FALSE, false",
    "False, false",
  })
  void parseTagsEnabled(String value, boolean expected) {
    assertThat(TagsStorage.parseTagsEnabled(value), is(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"maybe", "1", "0", "yes", "no", ""})
  void parseTagsEnabledRejectsInvalidValues(String value) {
    assertThrows(IllegalArgumentException.class, () -> TagsStorage.parseTagsEnabled(value));
  }
}
