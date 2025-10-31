package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.ObAuthClient;
import com.mvp.ob.ObClientProperties;
import com.mvp.ob.ObPaymentsClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Controller
public class PaymentsController {

  private final ObAuthClient authClient;
  private final ObPaymentsClient paymentsClient;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public PaymentsController(ObAuthClient authClient,
                            ObPaymentsClient paymentsClient,
                            ObClientProperties props) {
    this.authClient = authClient;
    this.paymentsClient = paymentsClient;
    this.props = props;
  }

  /* ---------- формы ---------- */

  /** Форма межбанковского перевода. */
  @GetMapping("/payments/interbank")
  public String interbankForm(
      @RequestParam(name = "bank", defaultValue = "v") String bank,
      @RequestParam(name = "login", required = false) String clientLogin,
      Model model
  ) {
    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("login", clientLogin);
    return "payments/interbank";
  }

  /** Сабмит межбанковского перевода. */
  @PostMapping("/payments/interbank")
  public String interbankSubmit(
      @RequestParam(name = "bank", defaultValue = "v") String bank,
      @RequestParam(name = "login") String clientLogin,
      @RequestParam(name = "debtorAccountId") String debtorAccountId,
      @RequestParam(name = "creditorIban") String creditorIban,
      @RequestParam(name = "amount") BigDecimal amount,
      @RequestParam(name = "currency", defaultValue = "EUR") String currency,
      @RequestParam(name = "description", required = false) String description,
      Model model
  ) {
    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("login", clientLogin);

    if (!StringUtils.hasText(clientLogin)) {
      model.addAttribute("error", "Login (client_id) не задан.");
      return "payments/interbank";
    }
    if (!StringUtils.hasText(debtorAccountId) || !StringUtils.hasText(creditorIban) || amount == null) {
      model.addAttribute("error", "Необходимо заполнить debtorAccountId, creditorIban и amount.");
      return "payments/interbank";
    }

    try {
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      String requestingBank = requestingBankFromBaseUrl(baseUrl);
      String clientId = clientLogin.trim();

      // NOTE: сигнатура метода может отличаться у твоего ObPaymentsClient.
      // Я использую наиболее типичные поля. Если имена не совпадут —
      // просто адаптируй вызов внутри try-блока.
      Map<String, Object> resp = paymentsClient.createInterbankPayment(
          baseUrl, token, clientId, debtorAccountId, creditorIban, amount, currency, description, requestingBank
      );

      model.addAttribute("paymentResponse", resp);
      String paymentId = value(resp, "data.id", "paymentId", "id");
      if (StringUtils.hasText(paymentId)) {
        return "redirect:/payments/%s?bank=%s&login=%s".formatted(paymentId, bank, clientId);
      }
      // если ID не вернули — остаёмся на форме и показываем «сырое» тело
      model.addAttribute("info", "Платёж создан, но paymentId не получен.");
      return "payments/interbank";

    } catch (ObPaymentsClient.ObApiException apiEx) {
      model.addAttribute("error", "Payment create failed: HTTP " + apiEx.getStatus().value());
      model.addAttribute("apiErrorBody", apiEx.getResponseBody());
      return "payments/interbank";
    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
      return "payments/interbank";
    }
  }

  /** Статус платежа по ID. */
  @GetMapping("/payments/{paymentId}")
  public String paymentStatus(
      @PathVariable("paymentId") String paymentId,
      @RequestParam(name = "bank", defaultValue = "v") String bank,
      @RequestParam(name = "login", required = false) String clientLogin,
      Model model
  ) {
    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("login", clientLogin);
    model.addAttribute("paymentId", paymentId);

    try {
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      String requestingBank = requestingBankFromBaseUrl(baseUrl);

      Map<String, Object> resp = paymentsClient.getPaymentStatus(baseUrl, token, paymentId, requestingBank);
      model.addAttribute("statusResponse", resp);
      model.addAttribute("paymentStatus", value(resp, "data.status", "status"));
      return "payments/status";

    } catch (ObPaymentsClient.ObApiException apiEx) {
      model.addAttribute("error", "Payment status failed: HTTP " + apiEx.getStatus().value());
      model.addAttribute("apiErrorBody", apiEx.getResponseBody());
      return "payments/status";
    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
      return "payments/status";
    }
  }

  /* ---------- helpers ---------- */

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  private String requestingBankFromBaseUrl(String baseUrl) {
    String u = (baseUrl == null ? "" : baseUrl).toLowerCase();
    if (u.contains("abank")) return "a";
    if (u.contains("sbank")) return "s";
    return "v";
  }

  @SuppressWarnings("unchecked")
  private String value(Map<String, Object> root, String... dottedPaths) {
    for (String p : dottedPaths) {
      try {
        String[] parts = p.split("\\.");
        Object cur = root;
        for (String part : parts) {
          if (!(cur instanceof Map<?,?> m) || !m.containsKey(part)) { cur = null; break; }
          cur = m.get(part);
        }
        if (cur != null) return String.valueOf(cur);
      } catch (Exception ignored) {}
    }
    return null;
  }
}
