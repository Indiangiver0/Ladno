# APP-004-AUTO-ENTRY v1 — выполнимость и контракты

Дата проверки источников: 2026-11-02. Автор: system-architect; проверяющий: architecture-lead. Статус: предложение, без реализации и самостоятельной приёмки.
Основание: APP-004, AGENTS.md, team/PROTOCOL.md, профиль роли; совместимость MoneyDTO сверена с STRUCTURE.md и finance/MODEL.md.
Подтверждено пользователем через root: личное использование, возможно папой; Android; доходы, расходы, категории, сканирование чеков, автоматический ввод из уведомлений; желаемый охват «все банки».
Не подтверждены: конкретные устройства/версии Android, банки/форматы, push или SMS, страна/языки чеков, валюты; совместный бюджет и синхронизация не следуют из использования папой.

## Выполнимость и зависимости

- Android — основное направление. `NotificationListenerService` существует с API18, получает системные события уведомлений; сервис объявляется с `BIND_NOTIFICATION_LISTENER_SERVICE` и service action [A1].
- Доступ отдельно выдаёт/отзывает пользователь в системных настройках notification listener [A2]. `POST_NOTIFICATIONS` разрешает приложению отправлять свои уведомления, не заменяет доступ listener [A3].
- Listener начинает работу после `onListenerConnected`; low-RAM устройства Android10 и ниже не поддерживают его; listener в work profile игнорируется, политики могут блокировать уведомления рабочего профиля [A1].
- Android15 ограничивает незаредактированное содержимое уведомлений с обнаруженным OTP для недоверенных listeners [A4]. Наличие доступа не гарантирует полный текст, доставку банковского события или банковскую историю.
- «Все банки» — цель покрытия: общий collector + сменные bank parsers + осторожный generic fallback. Реестр совместимости хранит пакет/версию шаблона/язык/проверенные случаи; непроверенный банк не объявляется поддержанным.
- Listener читает опубликованные уведомления приложений, не SMS-хранилище. Нужен ответ push/SMS; отдельная SMS-интеграция этим документом не обещается. Пропуск уведомления нельзя выдавать за отсутствие операции.
- Kotlin/native service — возможная рекомендация; UI, БД, версии SDK и сборочные инструменты выбираются отдельно после проверки среды. Для контрактов достаточно портов и одной транзакционной локальной БД, без обязательного сервера.
- Границы альтернатив: iOS UserNotifications и service extension обслуживают собственное приложение [I1,I2]. Вывод для обычного финансового приложения: общего чтения чужих push этими API нет.
- У Apple есть отдельный AccessoryNotifications для пересылки на разрабатываемый аксессуар: iPhone, accessory companion app; пользовательские установки только в ЕС с EU Apple Account [I3]. Это не подтверждённый путь данного MVP.
- Web `getNotifications` ограничен своим origin и своей service worker registration [W1]; вывод: сайт/PWA не заменяет Android listener банковских приложений.

## Минимальные модули и поток

`rawSourceEvent → parsedCandidate → transaction → optional category`; ручной ввод использует тот же валидатор операции, минуя OCR/listener.
- `capture`: Android listener/выбор изображения; фиксирует происхождение и отдельное событие, передаёт вход распознаванию; не создаёт денежные операции. Очередь сохраняет только разрешённые метаданные/кандидата; raw retention — отдельный AUTO-ENTRY-PERMISSIONS.md.
- `recognition`: локальный OCR или декодирование QR → текст/поля с координатами и качеством; не решает, что является расходом. Зависимость `TextRecognizer` заменяема.
- `parsing`: банк/шаблон либо чековый адаптер → кандидаты с доказательствами полей; generic fallback не угадывает недостающую валюту/тип. Обновление шаблона версионируется.
- `ingestion`: валидация, решение posted/review/link/ignore и дедупликация; единственная точка автопроведения через `LedgerRepository + UnitOfWork`.
- `ledger`: точные операции и редактирование; `categorization`: отдельное необязательное предложение категории; `presentation`: очередь проверки, исходный источник и исправление полей.
- Зависимости: UI/capture → ingestion → parsing/recognition ports + money/date/ledger; инфраструктура реализует порты. Банковские шаблоны не зависят от UI/БД, ledger не зависит от OCR/Android.

## Контракты и точность

- `RawSourceEvent`: `eventId`, `schemaVersion`, `sourceKind=notification|receipt|manual`, `capturedAt` UTC, `sourceIdentity`, `sourceRevision?`, `payloadRef?`, scoped keyed `payloadDigest`, `collectorVersion`. ID события не банковский ID; после удаления payload повторное распознавание/парсинг не обещается.
- Для notification отдельно: локальная установка/профиль, package, notification key/tag/id, `postTime`, признаки группировки/обновления/удаления, fingerprint полезного содержимого. Для receipt: image digest и распознанные reference/формат с пространством имён.
- `ParsedCandidate`: `candidateId`, `sourceEventIds[]`, `parserId/version`, `kind=income|expense|unknown`, `money?`, `operationDate?`, `merchant?`, `paymentRef?`, `fieldEvidence[]`, `confidence`, `reasons[]`, `state`.
- `fieldEvidence` связывает значение с полем/строкой/областью источника и способом получения; confidence по полям и общий `high|uncertain|unavailable` — оценка, не гарантия правильности и не произвольный процент точности.
- `Transaction`: стабильные `id`, `revision`, `kind=income|expense`, `MoneyDTO`, `operationDate`, `categoryId:null|id`, `sourceEventIds[]`; происхождение и журнал исправлений не стираются новым распознаванием.
- `MoneyDTO={currency,scale,amount:string}`: amount — положительная каноническая десятичная строка без группировки/экспоненты/плюса/ведущих нулей, ровно scale дробных знаков (scale=0 — без точки); UI, DTO, БД и экспорт передают её без float.
- Domain: `minor` — целое произвольной точности (`bigint`/`BigInteger`), единица `10^(-scale)` валюты; масштаб/код берутся из подтверждённого валютного каталога, не из OCR-догадки и не из timezone. Символ неоднозначен → `needs_review`.
- Ввод/распознавание нормализует разделители только при однозначном формате; недостающие дробные нули дополняются, лишние дробные знаки отклоняются без округления. Сложение/вычитание точные; разные валюты не складываются и не конвертируются.
- Валютный whitelist/scale, верхняя сумма/длина и границы дат требуют финансового согласования перед реализацией; превышение лимита даёт ошибку, никогда float/обрезание. Демо KZT не назначается валютой пользователя.
- `operationDate=YYYY-MM-DD` — проверенная календарная дата; отдельно хранятся доступные исходное время/смещение/зона и способ определения даты. `capturedAt/postTime` не подменяет дату операции молча; неизвестная дата/зона → review либо явно выбранная пользователем политика датирования.
- Период — `[начало выбранного дня/месяца, начало следующего)` по сохранённой operationDate; метаданные получения не сдвигают период. Отсутствующая категория не препятствует записи и не исключает расход из общего итога.

## Автопроведение, ошибки и исправление

- Автоматическая запись разрешается только при однозначных final income/expense, сумме, валюте/scale, дате, происхождении и отсутствии нерешённого конфликта идентичности; правила парсера должны пройти отдельные проверки. Категория для этого не нужна.
- `state=needs_review` при неизвестном шаблоне/скрытой сумме, нескольких итогах, конфликте валют/даты, сомнительном OCR, неясном типе или дубле. UI показывает причину и позволяет исправить/связать/отклонить; до решения кандидат не входит в денежные итоги.
- Pending/отказ/отмена, переводы, возвраты и другие unknown события не превращаются догадкой в income/expense. Их финансовую классификацию/связь с первоначальной операцией передать finance-lead; правила этого документа их не определяют.
- На чеке извлекается конечная оплаченная сумма покупки: не сдача, не переданные наличные, не сумма позиций плюс уже учтённый итог. Конфликт/несколько платежей/возвратный чек требуют review до отдельного правила.
- `categoryId=null` означает реальное отсутствие категории; «Прочее» автоматически не подставляется. Последующее назначение/исправление категории меняет ту же операцию и revision, а не создаёт новый расход.
- Нормализованные ошибки: `ACCESS_UNAVAILABLE`, `SOURCE_INCOMPLETE`, `OCR_UNAVAILABLE`, `UNSUPPORTED_FORMAT`, `FIELD_AMBIGUOUS`, `MONEY_INVALID`, `DATE_INVALID`, `IDENTITY_UNCERTAIN`, `CONFLICT`, `STORAGE_FAILED`; код+поле+безопасная причина, не сохранённый ноль.
- Проведение/связи источников/дедуп-индексы/state пишутся атомарно; успех только после commit. Конфликт revision не перезаписывает ручную правку; сбой допускает повтор той же команды без второй операции.

## Идентичность и защита от двойного учёта

- `StatusBarNotification.getKey()` — ключ записи уведомления, не bank transaction ID; postTime может отличаться от Notification.when [A5]. Разные уведомления могут описывать один платёж, а один key/id — обновления или разные покупки.
- Точный повтор обработки одного сохранённого `eventId/candidateId` идемпотентен: уникальная связь проведения возвращает прежний transactionId. Изменение версии парсера не создаёт повторную проводку.
- Collector различает lifecycle/revision: полный повтор того же key+postTime+payload внутри доказанно того же lifecycle связывается с прежним событием; hash одного текста или key отдельно не уникальны. После разрыва наблюдения неопределённость → review, не вечное подавление по key.
- Новый payload/postTime/reuseID не считается дублем автоматически: создаёт отдельное наблюдение. Обновлять существующую операцию можно лишь по надёжному paymentRef или подтверждению; без этого сохраняется кандидат, а не теряется потенциальная вторая покупка.
- Удаление уведомления закрывает его lifecycle, не отменяет денежную операцию; банковское сообщение об отмене проходит отдельную классификацию/review, не удаление по onNotificationRemoved.
- `paymentRef` сравнивается с пространством имён provider/account/reference-scheme; fiscal receipt ID и bank transaction ID не объявляются равными. Групповое уведомление не проводится вместе с уже разобранными дочерними событиями.
- Чек+push одного платежа: доказанный общий reference/связь либо подтверждение пользователя → несколько источников у одной операции, второе движение не создаётся; несовпадающие значения требуют решения, не скрытого перезаписывания.
- Одинаковые сумма/валюта и близкое время — только сигнал возможного дубля, не доказательство. При пересечении источников и недоказанной самостоятельности новой покупки второй кандидат остаётся needs_review с выбором «связать»/«отдельная операция».
- Без общего банковского/платёжного идентификатора автоматическое сопоставление всех чеков и push не гарантируется; проверка сохраняет обе реальные покупки. Нельзя обещать одновременно универсальный автопост и безошибочную дедупликацию.

## OCR и чековые форматы

- ML Kit Text Recognition v2 документирует Chinese/Devanagari/Japanese/Korean/Latin; Russian/Kazakh в его списке языков не заявлены [O1]. Цифры на чеке не доказывают понимание кириллицы и правильный выбор итога.
- ML Kit Android предлагает bundled модель либо загрузку через Google Play Services; до завершения загрузки unbundled модель не даёт результата [O2]. Локальность распознавания не означает готовность модели при первом офлайн-запуске.
- Локальный кандидат для проверки кириллицы: Tesseract с моделями `rus`/`kaz` в официальном наборе [O3]; движок допускает Android/iPhone targets [O4]. Это наличие моделей, не измеренная точность на чеках пользователя.
- Страна/язык/алфавит чеков пока неизвестны. OCR-движок выбирается по обезличенному корпусу реальных форматов; QR — отдельный адаптер конкретного формата/страны, единого QR для всех чеков не предполагается.

## Первичные источники — просмотрены 2026-11-02

- [A1: Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService); [A2: Settings, listener detail](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).
- [A3: Android POST_NOTIFICATIONS](https://developer.android.com/develop/ui/compose/notifications/notification-permission); [A4: Android15 OTP redaction](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction); [A5: StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification).
- [I1: Apple UserNotifications](https://developer.apple.com/documentation/usernotifications); [I2: собственный notification service extension](https://developer.apple.com/documentation/usernotifications/modifying-content-in-newly-delivered-notifications); [I3: AccessoryNotifications и ограничения ЕС](https://developer.apple.com/documentation/accessorynotifications).
- [W1: WHATWG Notifications, getNotifications](https://notifications.spec.whatwg.org/#dom-serviceworkerregistration-getnotifications).
- [O1: ML Kit scripts](https://developers.google.com/ml-kit/vision/text-recognition/v2) и [список языков](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages); [O2: модели Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
- [O3: Tesseract language data, rus/kaz](https://tesseract-ocr.github.io/tessdoc/Data-Files-in-different-versions.html); [O4: официальный manual](https://tesseract-ocr.github.io/tessdoc/).

## Доказательства, ограничения и передача

Выполнено: чтение входных документов; web open/find/search официальных Android/Apple/WHATWG/Google/Tesseract источников, сверка ограничений и языка моделей. Apple HTML показывал JS-заглушку; содержание проверено также индексируемыми официальными страницами, markdown fetch завершился unsupported content-type.
Не выполнялось: создание приложения, установка SDK/моделей, сборка, нативные/банковские/OCR испытания, чтение реальных уведомлений или изменение permissions. Это проверка выполнимости документацией, не проверка приложения; старые 46 файлов не изменялись этим исполнителем.
Открыто: push/SMS, конкретные Android/OEM/версии и банковские примеры; язык/страна чеков; валюты/лимиты/зона; финансовые правила special events; настройки доступа/минимизации — у security-engineer.
Следующее действие: architecture-lead проверяет N3/N4 и согласует вопросы с product/finance; затем независимые QA/суд проверяют зафиксированный пакет. После согласования нужен Android spike на целевых устройствах с повтором/обновлением/reuseID, двумя одинаковыми покупками, чеком+push и корпусом чеков; сейчас эти проверки не пройдены.
