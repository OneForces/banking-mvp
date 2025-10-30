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
     * POST {base}/account-consents/request — создать согласие.
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
              "permissions": ["ReadAccountsDetail","ReadBalances","ReadTransactions"],
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
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent request failed", rs);
                })
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(resp);

            String consentId     = asText(root.findValue("consent_id"));
            String status        = asText(root.findValue("status"));
            String requestId     = asText(root.findValue("request_id"));
            Boolean autoApproved = asBoolean(root.findValue("auto_approved"));
            boolean auto = autoApproved != null && autoApproved;

            if (StringUtils.hasText(consentId)) {
                if (!StringUtils.hasText(status)) status = "approved";
                return new ConsentCreateResult(consentId, status, requestId, auto);
            }
            if ("pending".equalsIgnoreCase(status) || StringUtils.hasText(requestId)) {
                return new ConsentCreateResult(null, StringUtils.hasText(status) ? status : "pending", requestId, auto);
            }
            throw new IllegalStateException("Unexpected consent response: " + resp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse consent response: " + resp, e);
        }
    }

    /** GET {base}/consents/{id} — статус согласия. */
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
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent status fetch failed", rs);
                })
                .body(String.class);
    }

    /** DELETE {base}/consents/{id} — отзыв согласия. */
    public String deleteConsent(
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

        RestClient.RequestHeadersSpec<?> req = http.delete()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent revoke failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/consents?client_id=... — список согласий клиента. */
    public String listConsents(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/consents")
                .queryParam("client_id", clientId)
                .build(true)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        // для списка согласий X-Consent-Id не требуется
        req = addAuthHeaders(req, bearerToken, null, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consents list fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts?client_id={clientId} — список счетов клиента. */
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
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Accounts fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId} — детали счета. */
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
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Account fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId}/balances — балансы. */
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
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Balances fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId}/transactions[?from=YYYY-MM-DD&to=YYYY-MM-DD] — операции. */
    public String getAccountTransactions(
            String bankBaseUrl,
            String bearerToken,
            String accountId,
            String consentId,
            String requestingBank,
            String fromDate,   // опционально
            String toDate      // опционально
    ) {
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}/transactions");

        if (StringUtils.hasText(fromDate)) ub.queryParam("from", fromDate);
        if (StringUtils.hasText(toDate))   ub.queryParam("to", toDate);

        URI uri = ub.buildAndExpand(accountId).toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Transactions fetch failed", rs);
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
        return req.header(HDR_X_REQUEST_ID, UUID.randomUUID().toString());
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

    /** Унифицированная ошибка OB-клиента. */
    private static ObApiException readAsObApiError(String prefix, ClientHttpResponse res) {
        HttpStatusCode status;
        String body = null;
        try {
            status = res.getStatusCode();
            try (InputStream is = res.getBody()) {
                if (is != null) body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            status = HttpStatusCode.valueOf(500);
        }
        String msg = prefix + ": HTTP " + status + (body != null && !body.isBlank() ? " — " + body : "");
        return new ObApiException(msg, status, body);
    }

    /** Рантайм-исключение с HTTP-кодом и телом ответа банка. */
    public static class ObApiException extends RuntimeException {
        private final HttpStatusCode status;
        private final String responseBody;

        public ObApiException(String message, HttpStatusCode status, String responseBody) {
            super(message);
            this.status = status;
            this.responseBody = responseBody;
        }

        public HttpStatusCode getStatus() { return status; }
        public String getResponseBody() { return responseBody; }
    }
}
