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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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

  @GetMapping("/accounts")
  public String list(@RequestParam(name = "bank", defaultValue = "v") String bank,
                     @RequestParam(name = "login") String customerLogin,
                     Model model) {

    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("login", customerLogin);

    if (!StringUtils.hasText(customerLogin)) {
      model.addAttribute("error", "Login (client_id) не задан.");
      return "accounts/index";
    }

    try {
      String clientId = customerLogin.trim();
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());

      // X-Requesting-Bank должен быть ID команды (например, "team101")
      String requestingBank = requestingBankFromBaseUrl(baseUrl);

      ConsentCreateResult consent = accountsClient.createConsent(baseUrl, token, clientId, requestingBank);

      String status = consent.getStatus();
      String consentId = consent.getConsentId();

      model.addAttribute("consentStatus", status);
      model.addAttribute("consentId", consentId);
      model.addAttribute("consentRequestId", consent.getRequestId());
      model.addAttribute("consentAutoApproved", consent.isAutoApproved());

      boolean consentReady = StringUtils.hasText(consentId) && isConsentActive(status);
      if (!consentReady) {
        model.addAttribute(
            "info",
            "Заявка на согласие отправлена и ожидает одобрения банка. request_id="
                + (consent.getRequestId() == null ? "—" : consent.getRequestId())
        );
        return "accounts/index";
      }

      try {
        String accountsJson = accountsClient.getAccounts(baseUrl, token, clientId, consentId, requestingBank);
        model.addAttribute("accountsJson", accountsJson);

        List<Map<String, Object>> accounts = extractAccounts(accountsJson);
        if (!accounts.isEmpty()) {
          model.addAttribute("accounts", accounts);
        }
      } catch (ObAccountsClient.ObApiException apiEx) {
        model.addAttribute("error", "Accounts fetch failed: HTTP " + apiEx.getStatus().value());
        model.addAttribute("apiErrorBody", apiEx.getResponseBody());
      }

    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
    }

    return "accounts/index";
  }

  /** Детали счёта + балансы по известным accountId и consentId. */
  @GetMapping("/accounts/{accountId}")
  public String details(@PathVariable("accountId") String accountId,
                        @RequestParam(name = "bank", defaultValue = "v") String bank,
                        @RequestParam(name = "consentId") String consentId,
                        @RequestParam(name = "login", required = false) String login,
                        Model model) {

    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("accountId", accountId);
    model.addAttribute("login", login);
    model.addAttribute("consentId", consentId);

    try {
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      // Используем ID команды как X-Requesting-Bank
      String requestingBank = requestingBankFromBaseUrl(baseUrl);

      String accJson = accountsClient.getAccountById(baseUrl, token, accountId, consentId, requestingBank);
      String balJson = accountsClient.getAccountBalances(baseUrl, token, accountId, consentId, requestingBank);

      model.addAttribute("account", safeToMap(accJson));
      model.addAttribute("balances", safeToMap(balJson));
    } catch (ObAccountsClient.ObApiException apiEx) {
      model.addAttribute("error", "Account fetch failed: HTTP " + apiEx.getStatus().value());
      model.addAttribute("apiErrorBody", apiEx.getResponseBody());
    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
    }

    return "accounts/details";
  }

  /** История транзакций по счёту. Даты from/to опциональны (YYYY-MM-DD). */
  @GetMapping("/accounts/{accountId}/transactions")
  public String transactions(@PathVariable("accountId") String accountId,
                             @RequestParam(name = "bank", defaultValue = "v") String bank,
                             @RequestParam(name = "consentId") String consentId,
                             @RequestParam(name = "login", required = false) String login,
                             @RequestParam(name = "from", required = false) String from,
                             @RequestParam(name = "to", required = false) String to,
                             Model model) {

    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("accountId", accountId);
    model.addAttribute("login", login);
    model.addAttribute("consentId", consentId);
    model.addAttribute("from", from);
    model.addAttribute("to", to);

    try {
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      // Используем ID команды как X-Requesting-Bank
      String requestingBank = requestingBankFromBaseUrl(baseUrl);
      String clientId = (login == null ? "" : login.trim());

      // 1) Проверяем статус согласия
      try {
        String consentJson = accountsClient.getConsentStatus(baseUrl, token, consentId, requestingBank);
        model.addAttribute("consentJson", consentJson);

        Map<String, Object> consentMap = safeToMap(consentJson);
        String status = null;
        List<String> permissions = List.of();

        Object topStatus = consentMap.get("status");
        if (topStatus instanceof String s) status = s;

        Object dataObj = consentMap.get("data");
        if (dataObj instanceof Map<?, ?> dm) {
          Object ds = dm.get("status");
          if (ds instanceof String s) status = s;
          Object perms = dm.get("permissions");
          if (perms instanceof List<?> pl) {
            List<String> out = new ArrayList<>();
            for (Object p : pl) if (p != null) out.add(String.valueOf(p));
            permissions = out;
          }
        }

        model.addAttribute("consentPermissions", permissions);
        model.addAttribute("consentStatusNormalized", normalizeStatus(status));

        if (!isConsentActive(status)) {
          model.addAttribute("error", "Consent is not approved (status=" + (status == null ? "unknown" : status) + ")");
          model.addAttribute("apiErrorBody", consentJson);
          return "accounts/transactions";
        }
      } catch (ObAccountsClient.ObApiException apiEx) {
        model.addAttribute("error", "Consent status fetch failed: HTTP " + apiEx.getStatus().value());
        model.addAttribute("apiErrorBody", apiEx.getResponseBody());
        return "accounts/transactions";
      }

      // 2) Транзакции
      String txJson = accountsClient.getAccountTransactions(
          baseUrl, token, accountId, consentId, requestingBank, clientId, from, to
      );
      model.addAttribute("transactionsJson", txJson);

      List<Map<String, Object>> tx = extractTx(txJson);
      if (!tx.isEmpty()) {
        model.addAttribute("transactions", tx);
      }
    } catch (ObAccountsClient.ObApiException apiEx) {
      model.addAttribute("error", "Transactions fetch failed: HTTP " + apiEx.getStatus().value());
      model.addAttribute("apiErrorBody", apiEx.getResponseBody());
    } catch (Exception e) {
      model.addAttribute("error", "Failed: " + e.getMessage());
    }

    return "accounts/transactions";
  }

  // ---------------- helpers ----------------

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  /**
   * ID команды для заголовка X-Requesting-Bank (например, "team101").
   * По спецификации это не код банка из URL, а именно идентификатор вашей команды.
   */
  private String requestingBankFromBaseUrl(String baseUrl) {
    // параметр baseUrl не используется намеренно; оставлен ради минимального диффа
    return props.getClientId();
  }

  private boolean isConsentActive(String status) {
    if (status == null) return false;
    String st = status.trim().toLowerCase();
    return st.equals("approved") || st.equals("authorized") || st.equals("authorised") || st.equals("valid");
  }

  private String normalizeStatus(String status) {
    if (status == null) return "unknown";
    String st = status.trim().toLowerCase();
    if (st.equals("authorised") || st.equals("authorized")) return "authorized";
    return st;
  }

  private List<Map<String, Object>> extractAccounts(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode arr = root.path("data").path("accounts");
      if (!arr.isArray()) arr = root.path("data").path("account");
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

  private Map<String, Object> safeToMap(String json) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> m = mapper.readValue(json, Map.class);
      return m;
    } catch (Exception e) {
      return Map.of("raw", json);
    }
  }

  private List<Map<String, Object>> extractTx(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode arr = root.path("data").path("transactions");
      if (!arr.isArray()) arr = root.path("data").path("transaction");
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
