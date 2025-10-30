// modules/app-portal/src/main/java/com/mvp/portal/controllers/AccountsController.java
package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.ConsentCreateResult;
import com.mvp.ob.ObAccountsClient;
import com.mvp.ob.ObAuthClient;
import com.mvp.ob.ObClientProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class AccountsController {

  private final ObAuthClient authClient;
  private final ObAccountsClient accountsClient;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public AccountsController(ObAuthClient authClient,
                            ObAccountsClient accountsClient,
                            ObClientProperties props) {
    this.authClient = authClient;
    this.accountsClient = accountsClient;
    this.props = props;
  }

  /**
   * Страница: создать согласие и показать счета клиента.
   * Пример: GET /accounts?bank=v&login=team101-1
   *
   * bank = v|a|s (по умолчанию v)
   * login = client_id клиента (например, team101-1)
   */
  @GetMapping("/accounts")
  public String list(@RequestParam(name = "bank", defaultValue = "v") String bank,
                     @RequestParam(name = "login") String customerLogin,
                     Model model) {

    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("login", customerLogin);

    try {
      // кто запрашивает (наш банк/команда), например "team101"
      String teamId = props.getClientId();
      // чей доступ/счета — логин клиента (например "team101-1")
      String clientId = customerLogin;

      // 1) bank-token по кредам команды
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());

      // 2) создаём согласие для клиента с контекстом нашего банка (teamId)
      ConsentCreateResult consent = accountsClient.createConsent(baseUrl, token, clientId, teamId);
      String status = consent.getStatus();
      model.addAttribute("consentStatus", status);
      model.addAttribute("consentRequestId", consent.getRequestId());
      model.addAttribute("consentAutoApproved", consent.isAutoApproved());

      // Если согласие не готово к использованию — показываем инфо и не идём за счетами.
      // Покрываем pending/processing/и отсутствие consentId на всякий случай.
      String consentId = consent.getConsentId();
      boolean consentReady = StringUtils.hasText(consentId)
          && "approved".equalsIgnoreCase(status); // при необходимости добавьте иные финальные статусы

      if (!consentReady) {
        model.addAttribute("info",
            "Заявка на согласие отправлена и ожидает одобрения банка. "
                + "request_id=" + (consent.getRequestId() == null ? "—" : consent.getRequestId()));
        // Можно вывести consentId, если он уже есть, но статус ещё не финальный
        if (StringUtils.hasText(consentId)) {
          model.addAttribute("consentId", consentId);
        }
        return "accounts/index";
      }

      // 3) согласие одобрено — тянем счета
      model.addAttribute("consentId", consentId);
      try {
        String accountsJson = accountsClient.getAccounts(baseUrl, token, clientId, consentId, teamId);
        model.addAttribute("accountsJson", accountsJson);

        List<Map<String, Object>> accounts = extractAccounts(accountsJson);
        if (!accounts.isEmpty()) {
          model.addAttribute("accounts", accounts);
        }
      } catch (RestClientResponseException httpEx) {
        // дружелюбная ошибка с кодом статуса
        model.addAttribute("error",
            "Accounts fetch failed: HTTP " + httpEx.getRawStatusCode() + " " + httpEx.getStatusText());
        model.addAttribute("apiErrorBody", httpEx.getResponseBodyAsString());
      }

    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
    }

    return "accounts/index";
  }

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  /** Поддерживаем обе возможные формы: data.accounts[] и data.account[]. */
  private List<Map<String, Object>> extractAccounts(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode arr = root.path("data").path("accounts");
      if (!arr.isArray()) {
        arr = root.path("data").path("account");
      }
      if (!arr.isArray()) return List.of();

      List<Map<String, Object>> out = new ArrayList<>();
      for (JsonNode n : arr) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = mapper.convertValue(n, Map.class);
        out.add(m);
      }
      return out;
    } catch (Exception e) {
      return List.of();
    }
  }
}
