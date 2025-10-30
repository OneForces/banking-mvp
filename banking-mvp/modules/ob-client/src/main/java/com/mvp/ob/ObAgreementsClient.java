package com.mvp.ob;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ObAgreementsClient {
    private final RestClient http = RestClient.builder().build();
    // Заглушка: при необходимости добавишь методы
}
