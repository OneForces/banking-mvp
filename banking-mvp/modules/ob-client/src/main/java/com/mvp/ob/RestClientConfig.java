package com.mvp.ob;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Configuration
public class RestClientConfig {

  @Bean
  public RestClient obRestClient(RestClient.Builder builder) {
    ClientHttpRequestInterceptor addRequestId =
        (request, body, execution) -> {
          request.getHeaders().addIfAbsent("X-Request-ID", UUID.randomUUID().toString());
          return execution.execute(request, body);
        };

    return builder
        .requestInterceptor(addRequestId)
        .build();
  }
}
