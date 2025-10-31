package com.mvp.ob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Связывается с application.yml по префиксу "app".
 * Поддерживает kebab-case ключи: client-id -> clientId и т.д.
 *
 * Пример настроек:
 *
 * app:
 *   vbank-base-url: https://vbank.open.bankingapi.ru
 *   abank-base-url: https://abank.open.bankingapi.ru
 *   sbank-base-url: https://sbank.open.bankingapi.ru
 *
 *   client-id: team101
 *   client-secret: secret
 *
 *   # опционально: заголовки x-fapi-*
 *   send-fapi-headers: true
 *   default-customer-ip: 203.0.113.10
 *   vbank-financial-id: vbank
 *   abank-financial-id: abank
 *   sbank-financial-id: sbank
 */
@ConfigurationProperties(prefix = "app")
public class ObClientProperties {

    // --- base URLs ---
    private String vbankBaseUrl;
    private String abankBaseUrl;
    private String sbankBaseUrl;

    // --- OAuth client ---
    private String clientId;
    private String clientSecret;

    // --- FAPI headers (optional, но полезно для транзакций) ---
    /** Включать ли автоматическую отправку x-fapi-* заголовков. */
    private boolean sendFapiHeaders = true;

    /** Какой IP подставлять в x-fapi-customer-ip-address/x-psu-ip-address (для песочниц допустим статический). */
    private String defaultCustomerIp = "203.0.113.10";

    /** Финансовые идентификаторы банка для x-fapi-financial-id. */
    private String vbankFinancialId;
    private String abankFinancialId;
    private String sbankFinancialId;

    // ---------- getters / setters ----------

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

    public boolean isSendFapiHeaders() { return sendFapiHeaders; }
    public void setSendFapiHeaders(boolean sendFapiHeaders) { this.sendFapiHeaders = sendFapiHeaders; }

    public String getDefaultCustomerIp() { return defaultCustomerIp; }
    public void setDefaultCustomerIp(String defaultCustomerIp) { this.defaultCustomerIp = defaultCustomerIp; }

    public String getVbankFinancialId() { return vbankFinancialId; }
    public void setVbankFinancialId(String vbankFinancialId) { this.vbankFinancialId = vbankFinancialId; }

    public String getAbankFinancialId() { return abankFinancialId; }
    public void setAbankFinancialId(String abankFinancialId) { this.abankFinancialId = abankFinancialId; }

    public String getSbankFinancialId() { return sbankFinancialId; }
    public void setSbankFinancialId(String sbankFinancialId) { this.sbankFinancialId = sbankFinancialId; }

    // ---------- helpers ----------

    /** Возвращает financial-id для кода банка (v/a/s). */
    public String financialIdFor(String bankCode) {
        String b = normalizeBankCode(bankCode);
        return switch (b) {
            case "a", "abank" -> abankFinancialId;
            case "s", "sbank" -> sbankFinancialId;
            default -> vbankFinancialId; // по умолчанию vbank
        };
    }

    /** Нормализуем код банка. */
    public static String normalizeBankCode(String bankCode) {
        return (bankCode == null) ? "v" : bankCode.toLowerCase();
    }

    /** Базовый URL по коду банка (если где-то понадобится). */
    public String baseUrlFor(String bankCode) {
        String b = normalizeBankCode(bankCode);
        return switch (b) {
            case "a", "abank" -> abankBaseUrl;
            case "s", "sbank" -> sbankBaseUrl;
            default -> vbankBaseUrl;
        };
    }

    /** Есть ли валидный financial-id для банка. */
    public boolean hasFinancialId(String bankCode) {
        String id = financialIdFor(bankCode);
        return StringUtils.hasText(id);
    }
}
