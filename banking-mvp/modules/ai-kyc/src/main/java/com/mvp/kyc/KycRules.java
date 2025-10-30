package com.mvp.kyc;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Простая реализация KYC: синтаксические правила + здравый смысл.
 * Позже сюда можно «подменить» на реальный AI или внешний провайдер.
 */
@Service
public class KycRules implements KycService {

  // Примерные шаблоны — подправь под свою страну
  private static final Pattern PASSPORT_RU = Pattern.compile("^[0-9]{2}\\s?[0-9]{2}\\s?[0-9]{6}$"); // 12 34 567890
  private static final Pattern PASSPORT_GENERIC = Pattern.compile("^[A-Z0-9\\-]{5,20}$");

  // 50 КБ как минимальный порог — защититься от «пустых» файлов
  private static final int MIN_IMAGE_SIZE = 50 * 1024;

  @Override
  public KycResult checkApplicant(String fullName,
                                  String passportNumber,
                                  byte[] idFront,
                                  byte[] idBack,
                                  byte[] selfie) {

    List<String> issues = new ArrayList<>();

    // ФИО
    if (fullName == null || fullName.trim().split("\\s+").length < 2) {
      issues.add("Некорректное ФИО");
    }

    // Паспорт
    if (passportNumber == null || passportNumber.isBlank()) {
      issues.add("Не указан номер паспорта");
    } else if (!(PASSPORT_RU.matcher(passportNumber.replaceAll("\\s+","")).matches()
          || PASSPORT_GENERIC.matcher(passportNumber).matches())) {
      issues.add("Номер паспорта не соответствует допустимому формату");
    }

    // Документы-изображения
    if (!hasMinBytes(idFront)) issues.add("Плохое качество/размер фронт-скана документа");
    // оборотная сторона может отсутствовать, но если передали — проверим
    if (idBack != null && idBack.length > 0 && !hasMinBytes(idBack)) {
      issues.add("Плохое качество/размер оборотной стороны документа");
    }
    // селфи опционально, но если есть — проверим
    if (selfie != null && selfie.length > 0 && !hasMinBytes(selfie)) {
      issues.add("Плохое качество/размер селфи");
    }

    // 🔒 тут можно добавить:
    // - проверку MRZ/штрихкодов
    // - сравнение селфи с фото в документе (FaceMatch)
    // - проверку по санкционным/PEP спискам
    // - проверку возраста и срока действия документа
