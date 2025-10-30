package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConsentController {
  private final ObAccountsClient accountsClient;
  private final BankTokenProvider tokenProvider;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public ConsentController(ObAccountsClient accountsClient, BankTokenProvider tokenProvider, ObClientProperties props) {
    this.accountsClient = accountsClient;
    this.tokenProvider = tokenProvider;
    this.props = props;
  }

  @GetMapping("/consents/{bank}/{id}/status")
  public String status(@PathVariable String bank, @PathVariable String id) {
    String baseUrl = switch (bank.toLowerCase()) {
      case "a","abank" -> props.getAbankBaseUrl();
      case "s","sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
    String token = tokenProvider.get(baseUrl);
    String json = accountsClient.getConsentStatus(baseUrl, token, id, props.getClientId());
    // Нормализуем до {"status":"approved|pending|rejected"}
    try {
      JsonNode r = mapper.readTree(json);
      String status = r.path("status").asText();
      return "{\"status\":\"" + status + "\"}";
    } catch (Exception e) {
      return "{\"status\":\"unknown\"}";
    }
  }
}
