package com.mvp.ob;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class ObPaymentsClient {

    private static final String HDR_X_REQUESTING_BANK = "X-Requesting-Bank";
    private static final String HDR_X_REQUEST_ID      = "X-Request-Id";

    private final RestClient http = RestClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObClientProperties props;

    public ObPaymentsClient(ObClientProperties props) {
        this.props = props;
    }

    /**
     * POST {base}/payments/interbank — межбанковский перевод.
     * Тело максимально «универсальное», под типовой JSON из методички.
     */
    public Map<String, Object> createInterbankPayment(
            String bankBaseUrl,
            String bearerToken,
            String clientId,
            String debtorAccountId,
            String creditorIban,
            BigDecimal amount,
            String currency,
            String description,
            String requestingBank // ID команды (teamXXX) если требуется бэком
    ) {
        String base = normalize(bankBaseUrl);

        // универсальное тело
        String bodyJson = """
        {
          "client_id": "%s",
          "debtor": { "account_id": "%s" },
          "creditor": { "iban": "%s" },
          "instructed_amount": { "amount": %s, "currency": "%s" },
          "description": %s,
          "requesting_bank": %s
        }
        """.formatted(
                escape(clientId),
                escape(debtorAccountId),
                escape(creditorIban),
                amount == null ? "0" : amount.stripTrailingZeros().toPlainString(),
                escape(currency == null ? "EUR" : currency),
                jsonOrNull(description),
                jsonOrNull(requestingBank)
        );

        RestClient.RequestHeadersSpec<?> req = http.post()
                .uri(base + "/payments/interbank")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(bodyJson);

        req = addAuthHeaders(req, bearerToken, requestingBank, bankBaseUrl);

        String resp = req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Payment create failed", rs);
                })
                .body(String.class);

        return readToMap(resp);
    }

    /** GET {base}/payments/{id} — статус платежа. */
    public Map<String, Object> getPaymentStatus(
            String bankBaseUrl,
            String bearerToken,
            String paymentId,
            String requestingBank
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalize(bankBaseUrl))
                .path("/payments/{id}")
                .buildAndExpand(paymentId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addAuthHeaders(req, bearerToken, requestingBank, bankBaseUrl);

        String resp = req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Payment status failed", rs);
                })
                .body(String.class);

        return readToMap(resp);
    }

    /* ----------------- helpers ----------------- */

    private RestClient.RequestHeadersSpec<?> addAuthHeaders(
            RestClient.RequestHeadersSpec<?> req,
            String bearerToken,
            String requestingBank,
            String bankBaseUrl
    ) {
        if (StringUtils.hasText(bearerToken)) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }

        // Если бэку нужен X-Requesting-Bank — отправляем то, что пришло (team id)
        if (StringUtils.hasText(requestingBank)) {
            req = req.header(HDR_X_REQUESTING_BANK, requestingBank);
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

    private Map<String, Object> readToMap(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private static String escape(String v) {
        return v == null ? "" : v.replace("\"","\\\"");
    }

    private static String jsonOrNull(String v) {
        return (v == null || v.isBlank()) ? "null" : "\"" + v.replace("\"","\\\"") + "\"";
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
