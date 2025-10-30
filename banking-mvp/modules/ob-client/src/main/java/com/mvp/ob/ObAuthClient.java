package com.mvp.ob;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Клиент авторизации к sandbox:
 * - получение bank-token по client_id/client_secret (с in-memory кэшем);
 * - login клиента (client-token) при необходимости.
 */
@Component
public class ObAuthClient {

  private final RestClient http = RestClient.builder().build();

  /** Кэш токенов: ключ = "{baseUrl}|{clientId}" */
  private final Map<String, TokenEntry> cache = new ConcurrentHashMap<>();

  /**
   * POST {base}/auth/bank-token?client_id=...&client_secret=...
   * Возвращает access_token из ответа. Токен кэшируется до истечения срока.
   */
  public String obtainBankToken(String bankBaseUrl, String clientId, String clientSecret) {
    final String cacheKey = bankBaseUrl + "|" + clientId;

    // 1) быстрый путь — валидный кэш
    TokenEntry cached = cache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      return cached.token;
    }

    // 2) запрос нового токена
    URI uri = UriComponentsBuilder
        .fromUriString(bankBaseUrl)
        .path("/auth/bank-token")
        .queryParam("client_id", clientId)
        .queryParam("client_secret", clientSecret)
        .build(true)
        .toUri();

    final String resp;
    try {
      resp = http.post()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(String.class);
    } catch (RestClientResponseException e) {
      // Вернуть информативную ошибку со статусом и "почищенным" телом
      String body = sanitize(e.getResponseBodyAsString());
      throw new IllegalStateException(
          "Consent request failed: HTTP " + e.getRawStatusCode() + " " + e.getStatusText()
              + " — " + (body == null ? "(empty body)" : body));
    } catch (RestClientException e) {
      throw new IllegalStateException("I/O error on POST " + uri + ": " + e.getMessage(), e);
    }

    String token = TokenUtils.extractAnyToken(resp);
    if (token == null) {
      throw new IllegalStateException("Token not found in response: " + sanitize(resp));
    }

    long ttlSec = extractExpiresInSeconds(resp); // обычно 86400
    // небольшой запас ~60 сек, чтобы не попасть в пограничные случаи
    Instant expiresAt = Instant.now().plusSeconds(Math.max(ttlSec - 60, 60));
    cache.put(cacheKey, new TokenEntry(token, expiresAt));

    return token;
  }

  /**
   * Логин клиента (получение client-token), если понадобится.
   */
  public String login(String baseUrl, String username, String password) {
    String body = """
        {"username":"%s","password":"%s"}
        """.formatted(username, password);

    final String resp;
    try {
      resp = http.post()
          .uri(baseUrl + "/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(String.class);
    } catch (RestClientResponseException e) {
      String eb = sanitize(e.getResponseBodyAsString());
      throw new IllegalStateException(
          "Login failed: HTTP " + e.getRawStatusCode() + " " + e.getStatusText()
              + " — " + (eb == null ? "(empty body)" : eb));
    } catch (RestClientException e) {
      throw new IllegalStateException("I/O error on POST " + baseUrl + "/auth/login: " + e.getMessage(), e);
    }

    String token = TokenUtils.extractAnyToken(resp);
    if (token == null) {
      throw new IllegalStateException("Token not found in response: " + sanitize(resp));
    }
    return token;
  }

  // --------- helpers ---------

  /** Наивное извлечение expires_in из JSON без зависимостей. */
  private static long extractExpiresInSeconds(String json) {
    try {
      String marker = "\"expires_in\":";
      int i = json.indexOf(marker);
      if (i >= 0) {
        int p = i + marker.length();
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        int q = p;
        while (q < json.length() && Character.isDigit(json.charAt(q))) q++;
        return Long.parseLong(json.substring(p, q));
      }
    } catch (Exception ignored) { }
    return 24 * 60 * 60; // дефолт на сутки
  }

  /** Убираем HTML/много пробелов и ограничиваем длину. */
  private static String sanitize(String s) {
    if (s == null || s.isBlank()) return null;
    String noTags = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    return noTags.length() > 400 ? noTags.substring(0, 400) + "…" : noTags;
  }

  private static final class TokenEntry {
    final String token;
    final Instant expiresAt;
    TokenEntry(String token, Instant expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }
    boolean isExpired() { return Instant.now().isAfter(expiresAt); }
  }
}
