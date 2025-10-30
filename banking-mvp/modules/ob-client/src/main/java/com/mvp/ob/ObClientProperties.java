package com.mvp.ob;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Связывается с application.yml по префиксу "app".
 * Поддерживает kebab-case ключи: client-id -> clientId и т.д.
 */
@ConfigurationProperties(prefix = "app")
public class ObClientProperties {

    private String vbankBaseUrl;
    private String abankBaseUrl;
    private String sbankBaseUrl;

    private String clientId;
    private String clientSecret;

    public String getVbankBaseUrl() { return vbankBaseUrl; }
    public void setVbankBaseUrl(String vbankBaseUrl) { this.vbankBaseUrl = vbankBaseUrl; }

    public String getAbankBaseUrl() { return abankBaseUrl; }
    public void setAbankBaseUrl(String abankBaseUrl) { this.abankBaseUrl = abankBaseUrl; }

    public String getSbankBaseUrl() { return sbankBaseUrl; }
    public void setSbankBaseUrl(String sbankBaseUrl) { this.sbankBaseUrl = sbankBaseUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
}
