package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.BankTokenProvider;
import com.mvp.ob.ObAccountsClient;
import com.mvp.ob.ObClientProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsentController {

  private final ObAccountsClient accountsClient;
  private final BankTokenProvider tokenProvider;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public ConsentController(ObAccountsClient accountsClient,
                           BankTokenProvider tokenProvider,
                           ObClientProperties props) {
    this.accountsClient = accountsClient;
    this.tokenProvider = tokenProvider;
    this.props = props;
  }

  /**
   * AJAX-пуллинг из UI: вернуть статус согласия (и нормализованный статус).
   * Пример: GET /consents/v/{id}/status
   * Ответ: {"status":"Authorised","normalized":"approved"}
   */
  @GetMapping("/consents/{bank}/{id}/status")
  public ResponseEntity<String> status(@PathVariable("bank") String bank,
                                       @PathVariable("id") String id) {
    if (!StringUtils.hasText(id)) {
      return okJson(mapToJson(Map.of("status", "unknown", "normalized", "unknown")));
    }

    String baseUrl = resolveBaseUrl(bank);
    try {
      String token = safeToken(baseUrl); // может быть пустой — клиент решит, нужен ли
      String json  = accountsClient.getConsentStatus(baseUrl, token, id, props.getClientId());

      String rawStatus = extractStatus(json);
      String normalized = normalizeStatus(rawStatus);

      return okJson(mapToJson(Map.of(
          "status", StringUtils.hasText(rawStatus) ? rawStatus : "unknown",
          "normalized", normalized
      )));
    } catch (Exception ignored) {
      // На любой ошибке не роняем UI-пуллинг
      return okJson(mapToJson(Map.of("status", "unknown", "normalized", "unknown")));
    }
  }

  /**
   * Отладочный endpoint: вернуть «сырой» JSON, который вернул банк-клиент.
   * Пример: GET /consents/v/{id}/status/raw
   */
  @GetMapping("/consents/{bank}/{id}/status/raw")
  public ResponseEntity<String> statusRaw(@PathVariable("bank") String bank,
                                          @PathVariable("id") String id) {
    if (!StringUtils.hasText(id)) {
      return okJson("{}");
    }
    String baseUrl = resolveBaseUrl(bank);
    try {
      String token = safeToken(baseUrl);
      String json  = accountsClient.getConsentStatus(baseUrl, token, id, props.getClientId());
      return okJson(json != null ? json : "{}");
    } catch (Exception ignored) {
      return okJson("{}");
    }
  }

  /* ==================== helpers ==================== */

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase(Locale.ROOT).trim();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      case "v", "vbank" -> props.getVbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  /** Аккуратно берём токен: если не получили, возвращаем пустую строку, чтобы не падать. */
  private String safeToken(String baseUrl) {
    try {
      String t = tokenProvider.get(baseUrl);
      return t == null ? "" : t;
    } catch (Exception e) {
      return "";
    }
  }

  /** Вытащить первое встреченное поле "status" из любых глубин JSON. */
  private String extractStatus(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode found = root.findValue("status");
      if (found != null && !found.isMissingNode() && !found.isNull()) {
        String v = found.asText(null);
        return StringUtils.hasText(v) ? v : null;
      }
    } catch (Exception ignored) { }
    return null;
  }

  /**
   * Приводим разные варианты к каноническим:
   * approved | pending | rejected | revoked | expired | unknown
   */
  private String normalizeStatus(String status) {
    if (!StringUtils.hasText(status)) return "unknown";
    String s = status.trim().toLowerCase(Locale.ROOT);

    // Активные
    if (s.equals("approved") || s.equals("authorised") || s.equals("authorized") || s.equals("valid"))
      return "approved";

    // Ожидание
    if (s.equals("pending") || s.equals("awaiting") || s.equals("awaiting_authorisation") || s.equals("awaiting_authorization"))
      return "pending";

    // Отклонено
    if (s.equals("rejected") || s.equals("denied") || s.equals("declined"))
      return "rejected";

    // Отозвано
    if (s.equals("revoked") || s.equals("cancelled") || s.equals("canceled"))
      return "revoked";

    // Истёк срок
    if (s.equals("expired") || s.equals("lapsed"))
      return "expired";

    return "unknown";
  }

  private ResponseEntity<String> okJson(String body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore()
            .mustRevalidate()
            .cachePrivate()
            .sMaxAge(Duration.ZERO))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  private String mapToJson(Map<String, ?> map) {
    try {
      return mapper.writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }
}
