package com.mvp.ob;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BankTokenProvider {
  private final ObAuthClient authClient;
  private final ObClientProperties props;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();
  private static final Duration SKEW = Duration.ofSeconds(30);
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  public BankTokenProvider(ObAuthClient authClient, ObClientProperties props) {
    this.authClient = authClient;
    this.props = props;
  }

  public synchronized String get(String baseUrl) {
    Entry e = cache.get(baseUrl);
    if (e != null && Instant.now().isBefore(e.exp.minus(SKEW))) return e.token;
    String token = authClient.obtainBankToken(baseUrl, props.getClientId(), props.getClientSecret());
    cache.put(baseUrl, new Entry(token, Instant.now().plus(DEFAULT_TTL)));
    return token;
  }

  private record Entry(String token, Instant exp) {}
}
