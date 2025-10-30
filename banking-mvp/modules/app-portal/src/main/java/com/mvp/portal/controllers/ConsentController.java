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
   * AJAX-пуллинг из UI: вернуть только статус согласия.
   * Пример: GET /consents/v/{id}/status
   * Ответ: {"status":"approved|pending|rejected|unknown"}
   */
  @GetMapping("/consents/{bank}/{id}/status")
  public ResponseEntity<String> status(@PathVariable("bank") String bank,
                                       @PathVariable("id") String id) {
    if (!StringUtils.hasText(id)) {
      return okJson("{\"status\":\"unknown\"}");
    }

    String baseUrl = resolveBaseUrl(bank);
    try {
      String token = tokenProvider.get(baseUrl);
      String json  = accountsClient.getConsentStatus(baseUrl, token, id, props.getClientId());

      // Нормализуем к {"status":"approved|pending|rejected|..."}
      String status = extractStatus(json);
      if (!StringUtils.hasText(status)) status = "unknown";
      return okJson("{\"status\":\"" + status + "\"}");
    } catch (Exception ignored) {
      // На любой ошибке не роняем UI-пуллинг
      return okJson("{\"status\":\"unknown\"}");
    }
  }

  /* --------- helpers --------- */

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  /** Пытаемся достать поле status из возможных форматов ответа. */
  private String extractStatus(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode found = root.findValue("status");
      if (found != null && !found.isMissingNode() && !found.isNull()) {
        String v = found.asText(null);
        return StringUtils.hasText(v) ? v : null;
      }
    } catch (Exception ignored) {
      // no-op
    }
    return null;
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
}
