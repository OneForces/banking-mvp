package com.mvp.kyc;

import java.util.List;

public interface KycService {

  /** Результат проверки заявителя. */
  record KycResult(boolean ok, List<String> issues) {
    public static KycResult pass() { return new KycResult(true, List.of()); }
    public static KycResult fail(List<String> issues) { return new KycResult(false, List.copyOf(issues)); }
  }

  /**
   * Базовая KYC-проверка документов.
   * @param fullName        ФИО
   * @param passportNumber  номер паспорта (или ID)
   * @param idFront         изображение/скан лицевой стороны
   * @param idBack          изображение/скан оборотной стороны (может быть null)
   * @param selfie          селфи (может быть null)
   */
  KycResult checkApplicant(String fullName,
                           String passportNumber,
                           byte[] idFront,
                           byte[] idBack,
                           byte[] selfie);
}
