package com.mvp.portal.controllers;

import com.mvp.ob.ObClientProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class HomeController {

    private final ObClientProperties props;

    public HomeController(ObClientProperties props) {
        this.props = props;
    }

    /** Главная страница */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** Универсальный health: /health/v, /health/a, /health/s и полные /health/vbank и т.п. */
    @GetMapping("/health/{bank}")
    public ResponseEntity<?> health(@PathVariable("bank") String bank) {
        String baseUrl = switch (bank.toLowerCase()) {
            case "a", "abank" -> nullToEmpty(props.getAbankBaseUrl());
            case "s", "sbank" -> nullToEmpty(props.getSbankBaseUrl());
            case "v", "vbank" -> nullToEmpty(props.getVbankBaseUrl());
            default -> "";
        };
        return ResponseEntity.ok(Map.of(
            "bank", bank,
            "baseUrl", baseUrl,
            "clientIdSet", hasText(props.getClientId()),
            "clientSecretSet", hasText(props.getClientSecret())
        ));
    }

    /** Для обратной совместимости со старыми ссылками */
    @GetMapping("/health/vbank")
    public ResponseEntity<?> healthVbank() {
        return ResponseEntity.ok(Map.of(
            "bank", "vbank",
            "baseUrl", nullToEmpty(props.getVbankBaseUrl()),
            "clientIdSet", hasText(props.getClientId()),
            "clientSecretSet", hasText(props.getClientSecret())
        ));
    }

    @GetMapping("/health/abank")
    public ResponseEntity<?> healthAbank() {
        return ResponseEntity.ok(Map.of(
            "bank", "abank",
            "baseUrl", nullToEmpty(props.getAbankBaseUrl()),
            "clientIdSet", hasText(props.getClientId()),
            "clientSecretSet", hasText(props.getClientSecret())
        ));
    }

    @GetMapping("/health/sbank")
    public ResponseEntity<?> healthSbank() {
        return ResponseEntity.ok(Map.of(
            "bank", "sbank",
            "baseUrl", nullToEmpty(props.getSbankBaseUrl()),
            "clientIdSet", hasText(props.getClientId()),
            "clientSecretSet", hasText(props.getClientSecret())
        ));
    }

    /** Сводка по всем базовым URL */
    @GetMapping("/health")
    public ResponseEntity<?> healthAll() {
        return ResponseEntity.ok(Map.of(
            "vbankBaseUrl", nullToEmpty(props.getVbankBaseUrl()),
            "abankBaseUrl", nullToEmpty(props.getAbankBaseUrl()),
            "sbankBaseUrl", nullToEmpty(props.getSbankBaseUrl()),
            "clientIdSet", hasText(props.getClientId()),
            "clientSecretSet", hasText(props.getClientSecret())
        ));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
