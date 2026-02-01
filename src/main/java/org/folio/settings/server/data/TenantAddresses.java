package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantAddresses {

  private List<TenantAddress> addresses;

  public TenantAddresses() {
  }

  public TenantAddresses(List<TenantAddress> addresses) {
    this.addresses = addresses;
  }

  public List<TenantAddress> getAddresses() {
    return addresses;
  }

  public void setAddresses(List<TenantAddress> addresses) {
    this.addresses = addresses;
  }
}
