package com.mvp.ob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ObAccountsClient {

    private static final String HDR_X_REQUESTING_BANK = "X-Requesting-Bank";
    private static final String HDR_X_CONSENT_ID      = "X-Consent-Id";
    private static final String HDR_X_REQUEST_ID      = "X-Request-ID";

    private final RestClient http = RestClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Создать согласие. Возвращает подробный результат (approved|pending).
     * Если банк авто-одобряет — будет consentId и status=approved.
     * Если требует ручного одобрения (например, SBank) — status=pending и requestId.
     */
    public ConsentCreateResult createConsent(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String requestingBank
    ) {
        String body = """
            {
              "client_id": "%s",
              "permissions": ["ReadAccountsDetail","ReadBalances"],
              "reason": "HackAPI demo consent"
            }
            """.formatted(clientId);

        String resp = http.post()
                .uri(normalize(bankBaseUrl) + "/account-consents/request")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(HDR_X_REQUESTING_BANK, requestingBank)
                .header(HDR_X_REQUEST_ID, UUID.randomUUID().toString())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw readAsObApiError("Consent request failed", res);
                })
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(resp);

            String consentId     = asText(root.findValue("consent_id"));
            String status        = asText(root.findValue("status"));         // "approved"|"pending"|...
            String requestId     = asText(root.findValue("request_id"));
            Boolean autoApproved = asBoolean(root.findValue("auto_approved"));
            boolean auto = autoApproved != null && autoApproved;

            // Если пришёл consent_id — считаем одобрено
            if (StringUtils.hasText(consentId)) {
                if (!StringUtils.hasText(status)) status = "approved";
                return new ConsentCreateResult(consentId, status, requestId, auto);
            }

            // Нет consent_id, но есть pending/request_id — возвращаем pending
            if ("pending".equalsIgnoreCase(status) || StringUtils.hasText(requestId)) {
                String s = StringUtils.hasText(status) ? status : "pending";
                return new ConsentCreateResult(null, s, requestId, auto);
            }

            // Иначе — неожиданный ответ
            throw new IllegalStateException("Unexpected consent response: " + resp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse consent response: " + resp, e);
        }
    }

    /** GET {base}/consents/{consentId} — получить статус согласия (для AJAX-пуллинга) */
    public String getConsentStatus(
            String bankBaseUrl,
            String bearerToken,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/consents/{id}")
                .buildAndExpand(consentId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (r, res) -> {
                    throw readAsObApiError("Consent status fetch failed", res);
                })
                .body(String.class);
    }

    /** GET {base}/accounts?client_id={clientId} */
    public String getAccounts(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts")
                .queryParam("client_id", clientId)
                .build(true)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (r, res) -> {
                    throw readAsObApiError("Accounts fetch failed", res);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId} */
    public String getAccountById(
            String bankBaseUrl,
            String bearerToken,
            String accountId,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}")
                .buildAndExpand(accountId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (r, res) -> {
                    throw readAsObApiError("Account fetch failed", res);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId}/balances */
    public String getAccountBalances(
            String bankBaseUrl,
            String bearerToken,
            String accountId,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}/balances")
                .buildAndExpand(accountId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (r, res) -> {
                    throw readAsObApiError("Balances fetch failed", res);
                })
                .body(String.class);
    }

    /* ------------ helpers ------------ */

    private RestClient.RequestHeadersSpec<?> addAuthHeaders(
            RestClient.RequestHeadersSpec<?> req,
            String bearerToken,
            String consentId,
            String requestingBank
    ) {
        if (StringUtils.hasText(bearerToken)) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        if (StringUtils.hasText(requestingBank)) {
            req = req.header(HDR_X_REQUESTING_BANK, requestingBank);
        }
        if (StringUtils.hasText(consentId)) {
            req = req.header(HDR_X_CONSENT_ID, consentId);
        }
        // общий request-id для трассировки
        req = req.header(HDR_X_REQUEST_ID, UUID.randomUUID().toString());
        return req;
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String asText(JsonNode node) {
        return node == null ? null : node.asText(null);
    }

    private static Boolean asBoolean(JsonNode node) {
        return node == null || node.isNull() ? null : node.asBoolean();
    }

    /** Унифицированное формирование понятной ошибки OB-клиента (RestClient). */
    private static ObApiException readAsObApiError(String prefix, ClientHttpResponse res) {
        HttpStatusCode status;
        String body = null;
        try {
            status = res.getStatusCode();
            try (InputStream is = res.getBody()) {
                if (is != null) {
                    body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            status = HttpStatusCode.valueOf(500);
        }
        String msg = prefix + ": HTTP " + status
                + (body != null && !body.isBlank() ? " — " + body : "");
        return new ObApiException(msg, status, body);
    }

    /** Рантайм-исключение с HTTP-кодом и телом ответа банка. Удобно показывать в UI. */
    public static class ObApiException extends RuntimeException {
        private final HttpStatusCode status;
        private final String responseBody;

        public ObApiException(String message, HttpStatusCode status, String responseBody) {
            super(message);
            this.status = status;
            this.responseBody = responseBody;
        }

        public HttpStatusCode getStatus() {
            return status;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
