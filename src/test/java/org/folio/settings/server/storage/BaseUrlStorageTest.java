package org.folio.settings.server.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseUrlStorageTest {

  @ParameterizedTest
  @CsvSource({
    "'', ''",
    "/, ''",
    "https://example.org, https://example.org",
    "https://example.org/, https://example.org",
    "https://example.org//, https://example.org",
  })
  void stripTrailingSlashes(String url, String expected) {
    assertThat(BaseUrlStorage.stripTrailingSlashes(url), is(expected));
  }

}
