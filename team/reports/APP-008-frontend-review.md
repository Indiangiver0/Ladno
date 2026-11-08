# APP-008 — проверка главы интерфейсного отдела

2026-11-03, Asia/Qyzylorda. Проверяющий `/root/improve_frontend_lead`; авторы продукта — отдельные `/root/improve_interface` и `/root/improve_interactions`. Глава продукт не редактировал. Это проверка исходников и чистой модели, не независимый QA и не свидетельство запуска Android-интерфейса.

## Версия

Проверены текущие точные байты:

- `www/view.js`: `964bd72a0204329c340d3a3778fc1d893d997f37001c0db68f199a5a602bed98`.
- `www/styles.css`: `eec74046d44208c6a878776a8a5bfa66eef5f6f1cb691894de30ff04c2aabb64`.
- `www/app.js`: `2ffcfeca79dd221b2c6043f1ac40b8b180bdcd2108609de43b5ac25920ed11fe`.
- `www/preview-adapter.js`: `1f096640f1cbc16d014cf535b20f51174a585e979d6ea68bfd9a6a0424000611`.
- Неизменённые `bridge.js`: `4241148a4a12c50eeccfb5965a1247711fdb07d5d369e1a1f825d39fdc98c6e2`; `index.html`: `7e9dd72daee9df50ab69b5b260f3f5c0bf087663c674a7fc75b2c1652a511e01`.
- Финансовая спецификация `docs/android/APP-008-CHARTS.md`: `c62cf40c21c5d023900d2ea40d39797ca5209ab63717cd5bf655add2230a2d96`; текущие эталоны `APP-008-CHART-FIXTURES.json`: `fec206c779684eb2b2b5d790888915b9d4a465ff48e8be5ffd747931f7a2f11b`.

## Проверка работ специалистов

**Представление.** Сверены `charts` в массиве вкладок, render switch и описаниях, кнопка «Графики расходов» на главной и пятая кнопка навигации. График использует выбранные месяц/валюту, exact summary cross-check, только expense из records; null-категория включена. Легенда показывает точные суммы/валюту/проценты, отличает одинаковые имена по ID, сохраняет все категории при top5+«Остальные». Есть empty/unavailable-состояния, полный круг для единственного сектора, пояснение малой доли. SVG использует DOM/presentation attributes и внешние классы, без inline-style; CSP не ослаблен.

**Взаимодействие.** Сверены имена `toggleInlineCategory`, `createInlineCategory`, `cancelInlineCategory`, `viewScanOutcome`, `dismissImport` между view и app. Создание категории вызывает отдельный saveCategory из button type=button; незаполненные amount/date основной формы не отправляются и не блокируют запрос. Перед запросом запоминаются все контролы существующей формы, включая скрытые identity/revision, decision, confirmDistinct и заметку; после ответа новый categoryId записывается именно в сохранённый draft, а paint выполняется без его перезаписи. Категория получает текущий kind формы, ошибки остаются в inline-блоке. Внешние refresh откладываются при открытом редакторе. Это подтверждено чтением потока, не выполнением DOM-сценария.

**Скан/разбор.** Job outcome согласован с native: posted/needs_review/linked/duplicate/ignored, nullable IDs/month/currency. На главной и в разборе показывается постоянный результат, а не один toast. Кнопка проверяет текущее наличие needs_review; устаревший needs_review не продолжает обещать непроведённый расход после разбора/убирания. Для transactionId запрашиваются нужные month/currency и текущая запись; недоступная операция не создаётся заново. «Убрать» доступно только receipt+needs_review, требует отдельного подтверждения и отправляет existing reviewCandidate decision=ignore с captured revision. Проведённые операции этот путь не удаляет. Bulk-действия нет; per-card обязательный сценарий реализован. Подсказка явно сообщает, что одиночный QR не импортирует расход и нужен текст всего чека.

**Предпросмотр.** Отдельный browser-only adapter остаётся пустым по операциям/источникам и отклоняет реальные scan/notification actions; добавлены 15 стартовых категорий и schemaVersion2. Native seed/миграцию принимает backend-глава и независимый Android QA; этот отчёт сохранность native БД не утверждает.

## Выполненные команды и доказательства

- Глава выполнил `node --check` для view.js, app.js, preview-adapter.js: все три завершились exit0. Это только синтаксис.
- Глава выполнил read-only Node VM: загрузил actual view.js без DOM, current finance fixture, рекурсивно сравнил все ожидаемые поля и JSON-типы для cases+invalidCases, затем убрал BigInt и проверил unavailable. **20/20 групп PASS**, exit0. Результаты авторского `app008-interface-author/results.json` также прочитаны, но их fixture hash относится к более ранним байтам; они не выданы за проверку текущих эталонов. Повтор главы использовал текущий `fec206…`.
- По исходникам проверен bounded-only Number в геометрии/процентах: суммы, сортировка, остатки и total остаются BigInt; largest remainder ties ASCII key, bps «Остальных» суммируются после распределения. Неподдерживаемый BigInt не ломает parse всего view.js: литералов нет, модель возвращает unavailable.
- `rg -n 'innerHTML|outerHTML|\.style|unsafe-inline|eval' android-app/app/src/main/assets/www`: совпадений нет. Index CSP прочитан отдельно; неизвестные тексты идут через textContent.
- Прочитан независимый расчётный QA на момент проверки: 60 модельных групп/3840 сравнений PASS. Это выполнено `/root/improve_qa_calculation`, а не главой. Найденный тем QA P1 ReceiptParser относится к backend и ещё исправляется отдельно; фронтенд-глава не снимает замечание.

## Решение и оставшиеся проверки

В пределах исходников и чистой модели согласованность двух специалистов подтверждена; новых подтверждённых блокирующих дефектов фронтенда не найдено. Передать конкретные байты независимому сценарному QA и суду. Обязательны browser/CUA проверки формы→inline category→сохранение, review→ignore/cancel/CAS и outcome-navigation, responsive/layout/accessibility. Сам глава не запускал браузер, Android WebView, камеру/галерею, клавиатуру и системные окна. Исходный пользовательский чек не воспроизведён; универсальное исправление OCR не утверждается. Итоговая сборка/подпись/обновление и безопасность native migration остаются вне этого заключения.
