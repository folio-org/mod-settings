package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantAddress {

  private String id;
  private String name;
  private String address;
  private Metadata metadata;

  /**
   * Create a tenant address.
   *
   * @param id address id
   * @param name address name
   * @param address address value
   */
  public TenantAddress(String id, String name, String address, Metadata metadata) {
    this.id = id;
    this.name = name;
    this.address = address;
    this.metadata = metadata;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }
}
