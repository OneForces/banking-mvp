// modules/app-portal/src/main/java/com/mvp/portal/controllers/DepositController.java
package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.ObAuthClient;
import com.mvp.ob.ObClientProperties;
import com.mvp.ob.ObProductsClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Controller
public class DepositController {

  private final ObAuthClient authClient;
  private final ObProductsClient productsClient;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public DepositController(ObAuthClient authClient,
                           ObProductsClient productsClient,
                           ObClientProperties props) {
    this.authClient = authClient;
    this.productsClient = productsClient;
    this.props = props;
  }

  /**
   * HTML-страница со списком продуктов.
   * Пример: GET /deposit/products?bank=v|a|s   (по умолчанию v)
   */
  @GetMapping("/deposit/products")
  public String products(@RequestParam(name = "bank", defaultValue = "v") String bank,
                         Model model) {
    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);

    String productsJson = null;
    List<ProductView> products = new ArrayList<>();

    try {
      // 1) получаем bank-token
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      if (!StringUtils.hasText(token)) {
        model.addAttribute("error", "Failed to obtain bank-token: empty token");
        return "deposit/products";
      }

      // 2) тянем продукты
      productsJson = productsClient.getProducts(baseUrl, token);
      model.addAttribute("productsJson", productsJson);

      // 3) гибко разбираем ответ
      for (JsonNode n : extractProductsArray(productsJson)) {
        products.add(ProductView.from(n));
      }

      if (products.isEmpty()) {
        model.addAttribute("info", "Нет продуктов для отображения.");
      }
    } catch (RestClientResponseException httpEx) {
      model.addAttribute("error",
          "Failed to fetch products: HTTP " + httpEx.getRawStatusCode() + " " + httpEx.getStatusText());
      model.addAttribute("apiErrorBody", httpEx.getResponseBodyAsString());
    } catch (Exception e) {
      model.addAttribute("error", "Failed to fetch products: " + e.getMessage());
    }

    model.addAttribute("products", products);
    return "deposit/products";
  }

  /**
   * JSON-эндпоинт для быстрой проверки интеграции без шаблонов.
   * Пример: GET /deposit/products.json?bank=v|a|s   (по умолчанию v)
   */
  @GetMapping(value = "/deposit/products.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> productsJson(@RequestParam(name = "bank", defaultValue = "v") String bank) {
    try {
      String baseUrl = resolveBaseUrl(bank);
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      if (!StringUtils.hasText(token)) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body("{\"error\":\"failed_to_obtain_bank_token\"}");
      }
      String productsJson = productsClient.getProducts(baseUrl, token);
      return ResponseEntity.ok(productsJson);
    } catch (RestClientResponseException httpEx) {
      return ResponseEntity.status(httpEx.getRawStatusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(httpBodyOrFallback(httpEx));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"error\":\"products_upstream_io\",\"message\":\"" + escape(e.getMessage()) + "\"}");
    }
  }

  /** Выбираем корректную baseUrl согласно банку. */
  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }

  /** Унифицированное извлечение массива продуктов из разных форм ответа. */
  private List<JsonNode> extractProductsArray(String json) {
    List<JsonNode> out = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(json);

      // 1) классика: {"data":{"product":[...]}}
      JsonNode arr = root.path("data").path("product");
      if (!arr.isArray()) {
        // 2) иногда: {"data":{"products":[...]}}
        arr = root.path("data").path("products");
      }
      if (!arr.isArray()) {
        // 3) иногда возвращают сразу массив верхнего уровня
        if (root.isArray()) arr = root;
      }
      if (arr.isArray()) {
        for (JsonNode n : arr) out.add(n);
      }
    } catch (Exception ignore) {
      // вернём пустой список — наверху уже есть вывод сырого JSON/ошибки
    }
    return out;
  }

  private static String httpBodyOrFallback(RestClientResponseException ex) {
    String body = ex.getResponseBodyAsString();
    if (body == null || body.isBlank()) {
      return "{\"error\":\"http_" + ex.getRawStatusCode() + "\",\"message\":\"" + escape(ex.getStatusText()) + "\"}";
    }
    return body;
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** Вью-модель карточки продукта. */
  public static class ProductView {
    public String productId;
    public String productType;
    public String productName;
    public String description;
    public String interestRate;
    public String minAmount;
    public String maxAmount;
    public Integer termMonths;

    static ProductView from(JsonNode n) {
      ProductView v = new ProductView();
      v.productId = text(n, "productId", "product_id", "id");
      v.productType = text(n, "productType", "product_type", "type");
      v.productName = text(n, "productName", "product_name", "name", "title");
      v.description = text(n, "description", "desc");
      v.interestRate = text(n, "interestRate", "interest_rate", "rate", "apr");
      v.minAmount = text(n, "minAmount", "min_amount", "min");
      v.maxAmount = text(n, "maxAmount", "max_amount", "max");
      v.termMonths = intOrNull(n, "termMonths", "term_months", "term");
      return v;
    }

    private static String text(JsonNode n, String... keys) {
      for (String k : keys) {
        JsonNode v = n.path(k);
        if (!v.isMissingNode() && !v.isNull()) return v.asText();
      }
      return null;
    }

    private static Integer intOrNull(JsonNode n, String... keys) {
      for (String k : keys) {
        JsonNode v = n.path(k);
        if (v.isInt()) return v.asInt();
        if (v.isNumber()) return v.numberValue().intValue();
        if (v.isTextual()) {
          try { return Integer.parseInt(v.asText()); } catch (Exception ignore) {}
        }
      }
      return null;
    }
  }
}
