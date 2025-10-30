// modules/ob-client/src/main/java/com/mvp/ob/ConsentCreateResult.java
package com.mvp.ob;

public class ConsentCreateResult {
  private final String status;      // "ok" | "pending" | "error"
  private final String consentId;   // если ok
  private final String requestId;   // если pending
  private final boolean autoApproved;

  public ConsentCreateResult(String status, String consentId, String requestId, boolean autoApproved) {
    this.status = status;
    this.consentId = consentId;
    this.requestId = requestId;
    this.autoApproved = autoApproved;
  }
  public String getStatus() { return status; }
  public String getConsentId() { return consentId; }
  public String getRequestId() { return requestId; }
  public boolean isAutoApproved() { return autoApproved; }
}
