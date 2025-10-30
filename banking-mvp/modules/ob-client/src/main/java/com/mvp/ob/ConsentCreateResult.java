// modules/ob-client/src/main/java/com/mvp/ob/ConsentCreateResult.java
package com.mvp.ob;

/**
 * Результат создания согласия.
 * status: "approved" | "pending" | "rejected" | "error"
 * consentId присутствует при approved, requestId — при pending.
 */
public class ConsentCreateResult {

  private final String consentId;    // при approved
  private final String status;       // approved | pending | ...
  private final String requestId;    // при pending
  private final boolean autoApproved;

  public ConsentCreateResult(String consentId, String status, String requestId, boolean autoApproved) {
    this.consentId = consentId;
    this.status = status;
    this.requestId = requestId;
    this.autoApproved = autoApproved;
  }

  public String getConsentId() { return consentId; }
  public String getStatus() { return status; }
  public String getRequestId() { return requestId; }
  public boolean isAutoApproved() { return autoApproved; }
}
