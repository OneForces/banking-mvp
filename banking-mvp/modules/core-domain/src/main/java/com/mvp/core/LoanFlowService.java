package com.mvp.core;

import com.mvp.kyc.KycService;
import com.mvp.kyc.KycService.KycResult;
import com.mvp.ob.ConsentCreateResult;
import com.mvp.ob.ObAccountsClient;
import com.mvp.ob.ObAgreementsClient;
import com.mvp.ob.ObClientProperties;
import com.mvp.ob.BankTokenProvider;
import org.springframework.stereotype.Service;

@Service
public class LoanFlowService {

  private final BankTokenProvider tokenProvider;
  private final ObAccountsClient accountsClient;
  private final ObAgreementsClient agreementsClient;
  private final ObClientProperties props;
  private final KycService kyc;

  public record LoanDecision(String status, String message, String agreementId) {}

  public LoanFlowService(BankTokenProvider tokenProvider,
                         ObAccountsClient accountsClient,
                         ObAgreementsClient agreementsClient,
                         ObClientProperties props,
                         KycService kyc) {
    this.tokenProvider = tokenProvider;
    this.accountsClient = accountsClient;
    this.agreementsClient = agreementsClient;
    this.props = props;
    this.kyc = kyc; // ← внедряется @Service KycRules
  }

  /** Пример оркестрации заявки на кредит. */
  public LoanDecision startApplication(String baseUrl,
                                       String customerLogin,
                                       String fullName,
                                       String passportNumber,
                                       byte[] idFront,
                                       byte[] idBack,
                                       byte[] selfie) {

    // 1) AI-KYC перед созданием согласия
    KycResult k = kyc.checkApplicant(fullName, passportNumber, idFront, idBack, selfie);
    if (!k.ok()) {
      return new LoanDecision("REJECTED", "AI-KYC failed: " + String.join("; ", k.issues()), null);
    }

    // 2) Получаем токен банка
    String bankToken = tokenProvider.get(baseUrl);
    String teamId = props.getClientId();

    // 3) Создаём согласие на доступ к счетам
    ConsentCreateResult consent = accountsClient.createConsent(baseUrl, bankToken, customerLogin, teamId);
    if (!"approved".equalsIgnoreCase(consent.getStatus())) {
      return new LoanDecision("PENDING", "Consent status: " + consent.getStatus(), null);
    }

    // 4) Открываем кредитное соглашение/карту (эндпоинт в ObAgreementsClient)
    String agreementId = agreementsClient.openLoanAgreement(
        baseUrl, bankToken, customerLogin, consent.getConsentId(), teamId
    );

    return new LoanDecision("APPROVED", "Loan agreement opened", agreementId);
  }
}
