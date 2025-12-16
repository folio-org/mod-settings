package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocaleSettings {

  private String locale;
  private String currency;
  private String timezone;
  private String numberingSystem;

  public LocaleSettings() {
  }

  /**
   * Initialize all fields.
   */
  public LocaleSettings(String locale, String currency, String timezone, String numberingSystem) {
    this.locale = locale;
    this.currency = currency;
    this.timezone = timezone;
    this.numberingSystem = numberingSystem;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getNumberingSystem() {
    return numberingSystem;
  }

  public void setNumberingSystem(String numberingSystem) {
    this.numberingSystem = numberingSystem;
  }

}
