package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/payments")
public class PaymentsController {

    private static final Logger log = LoggerFactory.getLogger(PaymentsController.class);

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    // === БАЗОВЫЕ URL (совпадает с application.yml) ===
    @Value("${app.vbank-base-url}")
    private String vBase;
    @Value("${app.abank-base-url}")
    private String aBase;
    @Value("${app.sbank-base-url}")
    private String sBase;

    // === Креды для /auth/bank-token ===
    @Value("${app.client-id}")
    private String clientId;          // teamXXX
    @Value("${app.client-secret}")
    private String clientSecret;

    // === FAPI/тех. заголовки ===
    @Value("${app.send-fapi-headers:true}")
    private boolean sendFapiHeaders;
    @Value("${app.default-customer-ip:203.0.113.10}")
    private String defaultIp;

    // (опционально) коды банков; используются для валидации/подсказок
    @Value("${app.vbank-financial-id:vbank}")
    private String vFinId;
    @Value("${app.abank-financial-id:abank}")
    private String aFinId;
    @Value("${app.sbank-financial-id:sbank}")
    private String sFinId;

    // ——————————————————————————— UI: форма ———————————————————————————
    @GetMapping("/interbank")
    public String interbankForm(
            @RequestParam(name = "bank", required = false, defaultValue = "v") String bank,
            @RequestParam(name = "login", required = false) String login,
            @RequestParam(name = "paymentConsentId", required = false) String paymentConsentId,
            Model model) {

        model.addAttribute("bank", bank);
        model.addAttribute("login", login);
        model.addAttribute("paymentConsentId", paymentConsentId);
        model.addAttribute("baseUrl", baseUrlOf(bank));
        return "payments/interbank";
    }

    // ——————————————————————————— Создание платежа ———————————————————————————
    @PostMapping("/interbank")
    public String interbankSubmit(
            @RequestParam("bank") String bank,
            @RequestParam("login") String login,
            @RequestParam("debtorAccountId") String debtorAccountId,
            @RequestParam("creditorIban") String creditorIban,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(name = "currency", defaultValue = "RUB") String currency,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "paymentConsentId", required = false) String paymentConsentId,
            Model model) {

        model.addAttribute("bank", bank);
        model.addAttribute("login", login);
        model.addAttribute("baseUrl", baseUrlOf(bank));

        try {
            // — тело запроса по методичке —
            Map<String, Object> initiation = new HashMap<>();

            Map<String, Object> instructedAmount = Map.of(
                    "amount", amount.toPlainString(),
                    "currency", StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "RUB"
            );
            Map<String, Object> debtorAccount = Map.of(
                    "schemeName", "RU.CBR.PAN",
                    "identification", debtorAccountId
            );

            Map<String, Object> creditorAccount = new HashMap<>();
            creditorAccount.put("schemeName", "RU.CBR.PAN");

            // Короткий синтаксис межбанка: "abank:4081..."
            if (StringUtils.hasText(creditorIban) && creditorIban.contains(":")) {
                String[] parts = creditorIban.split(":", 2);
                creditorAccount.put("identification", parts[1]);
                creditorAccount.put("bank_code", parts[0].trim().toLowerCase()); // vbank|abank|sbank
            } else {
                creditorAccount.put("identification", creditorIban);
            }

            initiation.put("instructedAmount", instructedAmount);
            initiation.put("debtorAccount", debtorAccount);
            initiation.put("creditorAccount", creditorAccount);
            if (StringUtils.hasText(description)) {
                initiation.put("remittanceInformation", description);
            }

            Map<String, Object> data = Map.of("initiation", initiation);
            Map<String, Object> body = Map.of("data", data);

            // HTTP
            String url = baseUrlOf(bank) + "/payments";
            HttpHeaders h = defaultHeaders(bank);

            // Если согласие на платеж передано — добавим заголовок
            if (StringUtils.hasText(paymentConsentId)) {
                h.set("x-payment-consent-id", paymentConsentId);
            }
            // Межбанковские кейсы могут ожидать "кто инициатор"
            h.set("x-requesting-bank", clientId);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, h);

            ResponseEntity<String> resp = http.exchange(url, HttpMethod.POST, req, String.class);
            JsonNode root = om.readTree(resp.getBody());
            JsonNode d = root.path("data");

            String paymentId = d.path("paymentId").asText(null);
            String status = d.path("status").asText(null);

            model.addAttribute("paymentId", paymentId);
            model.addAttribute("paymentStatus", status);
            model.addAttribute("statusResponse", root);
            return "payments/status";

        } catch (HttpStatusCodeException e) {
            String apiError = e.getResponseBodyAsString();
            log.warn("Payment create failed: {} {}", e.getStatusCode(), apiError);
            model.addAttribute("error", "Не удалось создать платёж: " + e.getStatusCode());
            model.addAttribute("apiErrorBody", apiError);
            return "payments/interbank";
        } catch (Exception e) {
            log.error("Payment create failed", e);
            model.addAttribute("error", "Не удалось создать платёж: " + e.getMessage());
            return "payments/interbank";
        }
    }

    // ——————————————————————————— Статус платежа ———————————————————————————
    @GetMapping("/status")
    public String paymentStatus(
            @RequestParam("bank") String bank,
            @RequestParam("login") String login,
            @RequestParam("paymentId") String paymentId,
            Model model) {

        model.addAttribute("bank", bank);
        model.addAttribute("login", login);
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("baseUrl", baseUrlOf(bank));

        try {
            String url = baseUrlOf(bank) + "/payments/" + paymentId;
            HttpEntity<Void> req = new HttpEntity<>(defaultHeaders(bank));
            ResponseEntity<String> resp = http.exchange(url, HttpMethod.GET, req, String.class);

            JsonNode root = om.readTree(resp.getBody());
            String status = root.path("data").path("status").asText(null);

            model.addAttribute("paymentStatus", status);
            model.addAttribute("statusResponse", root);
            return "payments/status";

        } catch (HttpStatusCodeException e) {
            String apiError = e.getResponseBodyAsString();
            log.warn("Get payment status failed: {} {}", e.getStatusCode(), apiError);
            model.addAttribute("error", "Не удалось получить статус: " + e.getStatusCode());
            model.addAttribute("apiErrorBody", apiError);
            return "payments/status";
        } catch (Exception e) {
            log.error("Get payment status failed", e);
            model.addAttribute("error", "Не удалось получить статус: " + e.getMessage());
            return "payments/status";
        }
    }

    // ——————————————————————————— helpers ———————————————————————————

    private String baseUrlOf(String bank) {
        return switch (bank == null ? "v" : bank) {
            case "a" -> aBase;
            case "s" -> sBase;
            default -> vBase;
        };
    }

    /** Базовые заголовки: Authorization + JSON (+FAPI по флагу) */
    private HttpHeaders defaultHeaders(String bank) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setBearerAuth(obtainBankToken(bank));

        if (sendFapiHeaders) {
            h.set("x-fapi-customer-ip-address", defaultIp);
            h.set("x-fapi-interaction-id", UUID.randomUUID().toString());
        }
        return h;
    }

    /** Получение bank-token согласно методичке: POST /auth/bank-token?client_id&client_secret */
    private String obtainBankToken(String bank) {
        try {
            String url = baseUrlOf(bank) + "/auth/bank-token"
                    + "?client_id=" + clientId
                    + "&client_secret=" + clientSecret;
            ResponseEntity<Map> resp = http.postForEntity(url, null, Map.class);
            Object token = resp.getBody() != null ? resp.getBody().get("access_token") : null;
            String t = token != null ? token.toString() : "";
            if (!StringUtils.hasText(t)) {
                log.warn("Empty access_token from {}", url);
            }
            return t;
        } catch (Exception e) {
            log.error("obtainBankToken failed", e);
            return "";
        }
    }
}
