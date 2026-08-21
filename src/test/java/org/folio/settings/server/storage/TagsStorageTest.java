package org.folio.settings.server.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

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
    assertThat(TagsStorage.parseTagsEnabled("tenant", value).result(), is(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"maybe", "1", "0", "yes", "no", ""})
  void parseTagsEnabledRejectsInvalidValues(String value) {
    var future = TagsStorage.parseTagsEnabled("tenant", value);
    assertThat(future.failed(), is(true));
    assertThat(future.cause().getMessage(), containsString("cannot parse tags_enabled value"));
  }
}
