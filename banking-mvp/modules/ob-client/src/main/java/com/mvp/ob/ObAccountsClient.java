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
    private static final String HDR_X_CONSENT_ID_ALT  = "X-Consent-ID";   // на всякий случай
    private static final String HDR_CONSENT_ID_ALT2   = "Consent-Id";     // на всякий случай
    private static final String HDR_X_REQUEST_ID      = "X-Request-Id";

    private final RestClient http = RestClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ObClientProperties props;

    public ObAccountsClient(ObClientProperties props) {
        this.props = props;
    }

    /** POST {base}/account-consents/request — создать согласие. */
    public ConsentCreateResult createConsent(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String requestingBank
    ) {
        String body = """
            {
              "client_id": "%s",
              "permissions": [
                "ReadAccountsDetail",
                "ReadBalances",
                "ReadTransactionsDetail"
              ],
              "reason": "HackAPI demo consent",
              "requesting_bank": "%s",
              "requesting_bank_name": "HackAPI App"
            }
            """.formatted(clientId, requestingBank);

        RestClient.RequestHeadersSpec<?> req = http.post()
                .uri(normalize(bankBaseUrl) + "/account-consents/request")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body);

        req = addAuthHeaders(req, bearerToken, null, requestingBank, bankBaseUrl);

        String resp = req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent request failed", rs);
                })
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(resp);
            JsonNode data = root.has("data") ? root.get("data") : root;

            String consentId = first(data, "consentId", "consent_id");
            String status    = first(data, "status");
            String requestId = first(data, "requestId", "request_id");

            Boolean autoApproved =
                    data.hasNonNull("autoApproved") ? data.get("autoApproved").asBoolean()
                    : data.hasNonNull("auto_approved") ? data.get("auto_approved").asBoolean()
                    : null;

            return new ConsentCreateResult(
                    StringUtils.hasText(consentId) ? consentId : null,
                    StringUtils.hasText(status) ? status : "pending",
                    requestId,
                    autoApproved != null && autoApproved
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse consent response: " + resp, e);
        }
    }

    /** GET {base}/account-consents/{id} — статус согласия. */
    public String getConsentStatus(
            String bankBaseUrl,
            String bearerToken,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/account-consents/{id}")
                .buildAndExpand(consentId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent status fetch failed", rs);
                })
                .body(String.class);
    }

    /** DELETE {base}/account-consents/{id} — отзыв согласия. */
    public String deleteConsent(
            String bankBaseUrl,
            String bearerToken,
            String consentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/account-consents/{id}")
                .buildAndExpand(consentId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.delete()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consent revoke failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/account-consents?client_id=... — список согласий клиента. */
    public String listConsents(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/account-consents")
                .queryParam("client_id", clientId)
                .build(true)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, null, requestingBank, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Consents list fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts?client_id={clientId}[&consent_id=...] — список счетов клиента. */
    public String getAccounts(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String consentId,
            String requestingBank
    ) {
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts")
                .queryParam("client_id", clientId);

        if (StringUtils.hasText(consentId)) {
            ub.queryParam("consent_id", consentId); // совместимость
        }

        URI uri = ub.build(true).toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

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
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}");

        if (StringUtils.hasText(consentId)) {
            ub.queryParam("consent_id", consentId); // совместимость
        }

        URI uri = ub.buildAndExpand(accountId).toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

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
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}/balances");

        if (StringUtils.hasText(consentId)) {
            ub.queryParam("consent_id", consentId); // совместимость
        }

        URI uri = ub.buildAndExpand(accountId).toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Balances fetch failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/accounts/{accountId}/transactions — операции. */
    public String getAccountTransactions(
            String bankBaseUrl,
            String bearerToken,
            String accountId,
            String consentId,
            String requestingBank,
            String clientId,   // не используется, оставлен для совместимости сигнатуры
            String fromDate,
            String toDate
    ) {
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/accounts/{accountId}/transactions");

        // спецификация: from_booking_date_time / to_booking_date_time (ISO)
        String fromIso = normalizeFromDate(fromDate);
        String toIso   = normalizeToDate(toDate);

        if (StringUtils.hasText(fromIso)) ub.queryParam("from_booking_date_time", fromIso);
        if (StringUtils.hasText(toIso))   ub.queryParam("to_booking_date_time", toIso);

        // client_id здесь не передаём
        if (StringUtils.hasText(consentId)) ub.queryParam("consent_id", consentId); // совместимость

        URI uri = ub.buildAndExpand(accountId).toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, consentId, requestingBank, bankBaseUrl);

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
            String requestingBank,
            String bankBaseUrl
    ) {
        if (StringUtils.hasText(bearerToken)) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }

        // X-Requesting-Bank — это ID команды (например "team101"), не код банка.
        if (StringUtils.hasText(requestingBank)) {
            req = req.header(HDR_X_REQUESTING_BANK, requestingBank);
        }

        if (StringUtils.hasText(consentId)) {
            req = req
                    .header(HDR_X_CONSENT_ID, consentId)
                    .header(HDR_X_CONSENT_ID_ALT, consentId)
                    .header(HDR_CONSENT_ID_ALT2, consentId);
        }

        if (props.isSendFapiHeaders()) {
            String bankCode = bankCodeFromBaseUrl(bankBaseUrl);
            String finId = props.financialIdFor(bankCode);
            if (StringUtils.hasText(finId)) {
                req = req.header("x-fapi-financial-id", finId);
            }
            String ip = props.getDefaultCustomerIp();
            if (StringUtils.hasText(ip)) {
                req = req.header("x-fapi-customer-ip-address", ip)
                         .header("x-psu-ip-address", ip)
                         .header("PSU-IP-Address", ip);
            }
            req = req.header("x-fapi-interaction-id", UUID.randomUUID().toString());
        }

        return req.header(HDR_X_REQUEST_ID, UUID.randomUUID().toString());
    }

    private static String bankCodeFromBaseUrl(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.toLowerCase();
        if (u.contains("abank")) return "a";
        if (u.contains("sbank")) return "s";
        return "v";
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String first(JsonNode node, String... names) {
        if (node == null) return null;
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull()) return v.asText(null);
        }
        return null;
    }

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

    // ----- даты для транзакций -----

    /** "YYYY-MM-DD" -> "YYYY-MM-DDT00:00:00Z"; если уже ISO — вернуть как есть. */
    private static String normalizeFromDate(String d) {
        if (!StringUtils.hasText(d)) return null;
        String s = d.trim();
        if (s.contains("T")) return s;
        return s + "T00:00:00Z";
    }

    /** "YYYY-MM-DD" -> "YYYY-MM-DDT23:59:59Z"; если уже ISO — вернуть как есть. */
    private static String normalizeToDate(String d) {
        if (!StringUtils.hasText(d)) return null;
        String s = d.trim();
        if (s.contains("T")) return s;
        return s + "T23:59:59Z";
    }

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
