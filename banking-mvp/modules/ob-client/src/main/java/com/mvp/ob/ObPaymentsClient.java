package com.mvp.ob;

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
import java.util.Map;
import java.util.UUID;

@Component
public class ObPaymentsClient {

    private static final String HDR_X_REQUESTING_BANK = "X-Requesting-Bank";
    private static final String HDR_X_CONSENT_ID      = "X-Consent-Id";
    private static final String HDR_X_REQUEST_ID      = "X-Request-Id";

    private final RestClient http = RestClient.builder().build();
    private final ObClientProperties props;

    public ObPaymentsClient(ObClientProperties props) {
        this.props = props;
    }

    /** POST {base}/payments — создать платёж. */
    public String createPayment(
            String bankBaseUrl,
            String bearerToken,
            String requestingBank,
            String consentId,          // может быть null, если не требуется
            String idempotencyKey,     // если null — сгенерим X-Request-Id сами
            String paymentBodyJson     // сырой JSON, соберём выше в контроллере
    ) {
        RestClient.RequestHeadersSpec<?> req = http.post()
                .uri(normalize(bankBaseUrl) + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(paymentBodyJson);

        req = addHeaders(req, bearerToken, requestingBank, consentId, idempotencyKey, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Payment create failed", rs);
                })
                .body(String.class);
    }

    /** GET {base}/payments/{id} — статус/детали платежа. */
    public String getPayment(
            String bankBaseUrl,
            String bearerToken,
            String requestingBank,
            String consentId,     // может быть null
            String paymentId
    ) {
        URI uri = UriComponentsBuilder.fromUriString(normalize(bankBaseUrl))
                .path("/payments/{id}")
                .buildAndExpand(paymentId)
                .toUri();

        RestClient.RequestHeadersSpec<?> req = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        req = addHeaders(req, bearerToken, requestingBank, consentId, null, bankBaseUrl);

        return req.retrieve()
                .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                    throw readAsObApiError("Payment fetch failed", rs);
                })
                .body(String.class);
    }

    /* ------------ helpers ------------ */

    private RestClient.RequestHeadersSpec<?> addHeaders(
            RestClient.RequestHeadersSpec<?> req,
            String bearerToken,
            String requestingBank,
            String consentId,
            String idempotencyKey,
            String bankBaseUrl
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

        // FAPI (как в ObAccountsClient)
        if (props.isSendFapiHeaders()) {
            String ip = props.getDefaultCustomerIp();
            if (StringUtils.hasText(ip)) {
                req = req.header("x-fapi-customer-ip-address", ip)
                         .header("x-psu-ip-address", ip)
                         .header("PSU-IP-Address", ip);
            }
            req = req.header("x-fapi-interaction-id", UUID.randomUUID().toString());
        }

        String requestId = StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString();
        return req.header(HDR_X_REQUEST_ID, requestId);
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
