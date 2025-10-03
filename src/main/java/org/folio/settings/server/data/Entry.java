package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.json.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Entry {

  private UUID id;

  private String scope;

  private String key;

  @JsonAnyGetter
  Map<String, Object> value = new LinkedHashMap<>();

  private UUID userId;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  @JsonAnySetter
  public void setValue(String key, Object val) {
    value.put(key, val);
  }

  /**
   * Get value as JsonObject.
   * @return JsonObject
   */
  @JsonIgnore
  public JsonObject getValue() {
    JsonObject ret = new JsonObject();
    value.forEach(ret::put);
    return ret;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  /**
   * Validate that required fields are present.
   * @throws IllegalArgumentException if a required field is missing
   */
  public void validate() {
    if (id == null) {
      throw new IllegalArgumentException("Entry must have an id");
    }
    if (scope == null) {
      throw new IllegalArgumentException("Entry must have a scope");
    }
    if (key == null) {
      throw new IllegalArgumentException("Entry must have a key");
    }
  }
}
