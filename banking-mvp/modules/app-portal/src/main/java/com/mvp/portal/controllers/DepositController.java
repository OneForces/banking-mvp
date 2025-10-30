// modules/app-portal/src/main/java/com/mvp/portal/controllers/DepositController.java
package com.mvp.portal.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvp.ob.ObAuthClient;
import com.mvp.ob.ObClientProperties;
import com.mvp.ob.ObProductsClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class DepositController {

  private final ObAuthClient authClient;
  private final ObProductsClient productsClient;
  private final ObClientProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public DepositController(
      ObAuthClient authClient,
      ObProductsClient productsClient,
      ObClientProperties props
  ) {
    this.authClient = authClient;
    this.productsClient = productsClient;
    this.props = props;
  }

  /**
   * HTML-страница со списком продуктов.
   * Пример: GET /deposit/products?bank=v|a|s   (по умолчанию v)
   */
  @GetMapping("/deposit/products")
  public String products(
      @RequestParam(name = "bank", defaultValue = "v") String bank,
      Model model
  ) {
    String baseUrl = resolveBaseUrl(bank);
    model.addAttribute("bank", bank.toLowerCase());
    model.addAttribute("baseUrl", baseUrl);

    String productsJson = null;
    List<ProductView> products = new ArrayList<>();

    try {
      String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
      productsJson = productsClient.getProducts(baseUrl, token);

      // ожидаем {"data":{"product":[ ... ]}}
      JsonNode root = mapper.readTree(productsJson);
      JsonNode arr = root.path("data").path("product");
      if (arr.isArray()) {
        for (JsonNode n : arr) {
          products.add(ProductView.from(n));
        }
      } else {
        model.addAttribute("error", "Unexpected API shape: 'data.product' is not array");
      }
    } catch (Exception e) {
      model.addAttribute("error", "Failed to fetch products: " + e.getMessage());
    }

    model.addAttribute("products", products);
    model.addAttribute("productsJson", productsJson);
    return "deposit/products";
  }

  /**
   * JSON-эндпоинт для быстрой проверки интеграции без шаблонов.
   * Пример: GET /deposit/products.json?bank=v|a|s   (по умолчанию v)
   */
  @GetMapping(value = "/deposit/products.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> productsJson(
      @RequestParam(name = "bank", defaultValue = "v") String bank
  ) {
    String baseUrl = resolveBaseUrl(bank);
    String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
    String productsJson = productsClient.getProducts(baseUrl, token);
    return ResponseEntity.ok(productsJson);
  }

  private String resolveBaseUrl(String bank) {
    String b = bank == null ? "v" : bank.toLowerCase();
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
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
      v.productId = text(n, "productId", "product_id");
      v.productType = text(n, "productType", "product_type");
      v.productName = text(n, "productName", "product_name", "name");
      v.description = text(n, "description");
      v.interestRate = text(n, "interestRate", "interest_rate", "rate");
      v.minAmount = text(n, "minAmount", "min_amount");
      v.maxAmount = text(n, "maxAmount", "max_amount");
      v.termMonths = intOrNull(n, "termMonths", "term_months");
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
