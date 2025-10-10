package org.folio.settings.server.data;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Assertions;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class EntryTest {

    @ParameterizedTest
    @CsvSource({
        "123e4567-e89b-12d3-a456-426614174000, s, k, ",
        "                                    , s, k, 'Entry must have an id'",
        "123e4567-e89b-12d3-a456-426614174000,  , k, 'Entry must have a scope'",
        "123e4567-e89b-12d3-a456-426614174000, s,  , 'Entry must have a key'",
    })
    void testValidate(UUID id, String scope, String key, String expectedMessage) {
        var entry = new Entry();
        entry.setId(id);
        entry.setScope(scope);
        entry.setKey(key);
        if (expectedMessage == null) {
            Assertions.assertDoesNotThrow(entry::validate);
        } else {
            var thrown = Assertions.assertThrows(IllegalArgumentException.class, entry::validate);
            assertThat(thrown.getMessage(), is(expectedMessage));
        }
    }
}
