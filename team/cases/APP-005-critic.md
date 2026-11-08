# APP-005 — первоначальная независимая позиция критика

Задача: оценить A1–A6 и передачу Android-альфы отдельно от полной готовности. Критик `/root/android_court_critic` не участвовал в реализации или проверке разработчиков. Позиции адвокатов до записи не читал, с ними не общался; продукт не изменял.

Версия: `android-alpha-v1`, 108 файлов, manifest SHA-256 `87b8a045ac6e104e802cca11e6bbae40cceb5d688e13e681e654a990f4f99864`; APK SHA-256 `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`.

## Выполненное и доказательства

- Прочитаны AGENTS/PROTOCOL, полный профиль критика, APP-005 case/task, требования и отчёты QA; выборочно проверены отчёты глав, security findings, README, native ingress и LedgerStore ingest/review. Это чтение, не повтор полного QA или запуск Android.
- Собственная read-only сверка всех файлов snapshot: ожидание — 108 совпадающих SHA и заданный canonical digest; факт — `files=108`, `mismatches=[]`, digest совпал, exit0. Команда из корня:

```powershell
python -c 'import hashlib,json,pathlib; r=pathlib.Path("."); s=json.loads((r/"team/snapshots/android-alpha-v1.json").read_text(encoding="utf-8")); bad=[x["path"] for x in s["files"] if hashlib.sha256((r/x["path"]).read_bytes()).hexdigest()!=x["sha256"].lower()]; c="".join(x["sha256"]+"  "+x["path"]+chr(10) for x in s["files"]); print(json.dumps(dict(files=len(s["files"]),mismatches=bad,canonicalDigest=hashlib.sha256(c.encode()).hexdigest(),expected=s["manifestSha256"])))'
```

- `Get-Content team/reports/APP-005-qa-review.md` и оба `android-app/qa/*/REPORT.md`: ожидание — различимые области исполнения; факт — 211 JVM/39 host SQLite/17 browser groups/55 APK checks PASS относятся к своим областям; Android execution явно отсутствует. Эти прогоны выполнил QA, я их не повторял.
- QA-CALC-01 не оставляю открытым: [независимый повтор](../../android-app/qa/calculation/REPORT.md) подтверждает review для неоплаченного чека и положительные paid cases на финальном ReceiptParser SHA; сверка snapshot привязывает доказательство к делу. Работа OCR→SQLite этим не доказана.

## Оценка A1–A6

- A1: APK и сборочная идентичность подтверждены [финальными APK-проверками](../../android-app/qa/scenario/REPORT.md), [build-result](../../android-app/dist/build-result.json) и snapshot. Установка/работа на конкретном телефоне не подтверждены; A1 оставляет установку действием пользователя.
- A2: частично. Browser CRUD и host DDL не исполняют Android bridge→LedgerStore→restart.
- A3: частично. NLS есть в APK, parser исполнялся JVM; delivery/revoke/реальные уведомления Android не исполнялись. «Все банки» не доказано и не должно объявляться покрытием.
- A4: частично. Модели/JNI библиотеки упакованы, finality parser проверен; камера/галерея→Tesseract→авторасход не исполнялись.
- A5: частично. Точность core и ограничения DDL подтверждены; native aggregate, повтор после edit/delete, receipt+notification и post/link/ignore не исполнены интеграционно.
- A6: главы и независимый QA представлены; судебный обмен и отдельный вердикт ещё должны состояться. Собственная первоначальная позиция не завершает суд.

## Замечания

**C005-01 / P2 / подтверждённый пробел доказательств, не подтверждённая ошибка продукта.** Полная функциональная приёмка A2–A5 сейчас не обоснована. Ожидание: наблюдаемый результат обязательных сценариев на Android; факт: [матрица QA](../reports/APP-005-qa-review.md) прямо оставляет эти критерии частичными; [scenario ограничения](../../android-app/qa/scenario/REPORT.md) и [calculation ограничения](../../android-app/qa/calculation/REPORT.md) подтверждают отсутствие соответствующих запусков. Приёмку исходного запроса блокирует недостаточность доказательств, а не установленный отказ приложения. Сборка, static review и host SQL этот пробел не закрывают.

Открытых воспроизводимых P0/P1 по изученному пакету не установил. Не добавляю гипотетические сбои банков/OEM/OCR как доказанные дефекты. Отсутствие таких findings не подтверждает невыполненные сценарии.

## Оставшиеся вопросы и следующее действие

Для полной приёмки: Android fresh install→setup→CRUD/categories→restart; реальная доставка завершённого/сомнительного notification и replay/revoke; синтетический оплаченный/нечёткий чек через media→OCR; edit/delete/replay и receipt+notification с post/link/ignore. Ожидания и факты сохранить по той же версии; устройство может быть тестовым, личные данные пользователя не необходимы.

Рекомендация судье по полной готовности: **insufficient_evidence**, пока C005-01 открыт. Передачу существующего APK **как альфы для личной установки и следующей проверки** считаю допустимой при прямом сообщении: APK собран; установка/запуск Android, persistence, OCR и NLS end-to-end не проверялись; поддержка всех банков не установлена. Это ограниченная передача артефакта, не закрытие A2–A5 и не выполнение исходного запроса целиком. Отдельного согласия на публикацию она не даёт.

После раскрытия отвечу на конкретные доводы обоих адвокатов в отдельном reply; судье передаётся весь спор.
