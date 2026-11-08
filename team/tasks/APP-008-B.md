# APP-008-B: чеки и стартовые категории

- Родитель: APP-008; статус qa; отдел backend-data; глава /root/improve_backend_lead.
- Цель и приёмка: A1/A2 родителя, safe structured scan outcome, улучшение извлечения итогов без ложной автоматической оплаты и дубликатов; idempotent seed fresh/upgrade без потери данных.
- Владелец native: backend-engineer (назначение после плана главы) NativeRuntime/MainActivity/OcrEngine/core/ReceiptParser.java/core ParserTest. Владение data: data-engineer storage/seed/schema/связанные тесты. Глава проверяет, не пишет реализацию.
- Исходный чек пользователя не предоставлен; подтверждённое наблюдение: открылась форма правок с «—» вместо распознанного итога. Возможная причина parser не равна воспроизведённому случаю.
- Проверка: специалисты → глава → независимый QA → общий суд APP-008.
