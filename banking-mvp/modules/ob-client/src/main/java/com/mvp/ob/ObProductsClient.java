package com.mvp.ob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Класс для работы с продуктами банка.
 */
@Component
public class ObProductsClient {

  private final RestClient http = RestClient.builder().build();
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * GET {base}/products
   * Если token не пустой — добавляем Authorization: Bearer.
   * Возвращаем «сырую строку JSON».
   */
  public String getProducts(String bankBaseUrl, String bearerToken) {
    return getProducts(bankBaseUrl, bearerToken, null);
  }

  /**
   * GET {base}/products?product_type={productType}
   * Возвращаем «сырую строку JSON».
   */
  public String getProducts(String bankBaseUrl, String bearerToken, String productType) {
    String uri = UriComponentsBuilder.fromUriString(bankBaseUrl)
        .path("/products")
        .queryParamIfPresent("product_type", productType == null || productType.isBlank()
            ? java.util.Optional.empty()
            : java.util.Optional.of(productType))
        .build(true)
        .toUriString();

    RestClient.RequestHeadersSpec<?> req = http.get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON);

    if (bearerToken != null && !bearerToken.isBlank()) {
      req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
    }
    return req.retrieve().body(String.class);
  }

  /**
   * Удобный метод: получить список DTO Product без фильтра.
   */
  public List<Product> getProductsList(String bankBaseUrl, String bearerToken) {
    return getProductsList(bankBaseUrl, bearerToken, null);
  }

  /**
   * Удобный метод: получить список DTO Product c product_type-фильтром.
   */
  public List<Product> getProductsList(String bankBaseUrl, String bearerToken, String productType) {
    String json = getProducts(bankBaseUrl, bearerToken, productType);
    try {
      JsonNode root = mapper.readTree(json);

      // чаще всего: { "data": { "product": [ ... ] } }
      JsonNode arr = root.path("data").path("product");

      // возможные альтернативы:
      if (!arr.isArray()) {
        if (root.isArray()) {
          arr = root; // [ ... ]
        } else if (root.has("products") && root.get("products").isArray()) {
          arr = root.get("products"); // { "products": [ ... ] }
        } else {
          arr = mapper.createArrayNode();
        }
      }

      List<Product> out = new ArrayList<>();
      for (JsonNode n : arr) {
        out.add(new Product(
            textOrNull(n, "productId", "product_id", "id"),
            textOrNull(n, "productType", "product_type", "type"),
            textOrNull(n, "productName", "product_name", "name"),
            textOrNull(n, "description"),
            textOrNull(n, "interestRate", "interest_rate", "rate"),
            textOrNull(n, "minAmount", "min_amount", "min"),
            textOrNull(n, "maxAmount", "max_amount", "max"),
            intOrNull(n, "termMonths", "term_months")
        ));
      }
      return out;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse products JSON: " + e.getMessage(), e);
    }
  }

  private static String textOrNull(JsonNode n, String... candidates) {
    for (String c : candidates) {
      JsonNode x = n.get(c);
      if (x != null && !x.isNull()) return x.asText();
    }
    return null;
  }

  private static Integer intOrNull(JsonNode n, String... candidates) {
    for (String c : candidates) {
      JsonNode x = n.get(c);
      if (x != null && !x.isNull()) return x.isInt() ? x.asInt() : parseIntSafe(x.asText());
    }
    return null;
  }

  private static Integer parseIntSafe(String s) {
    try { return (s == null || s.isBlank()) ? null : Integer.valueOf(s); }
    catch (NumberFormatException ex) { return null; }
  }
}
