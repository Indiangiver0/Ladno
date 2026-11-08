(function (root) {
  'use strict';
  // Browser-only, volatile test adapter. It never reads storage or creates source events.
  if (typeof root.AndroidApp !== 'undefined' && root.AndroidApp !== null) return;
  var currencies = [
    ['AUD', 2], ['BHD', 3], ['CAD', 2], ['CHF', 2], ['CNY', 2], ['EUR', 2], ['GBP', 2],
    ['JPY', 0], ['KGS', 2], ['KRW', 0], ['KWD', 3], ['KZT', 2], ['RUB', 2], ['USD', 2], ['UZS', 2]
  ].map(function (entry) { return { code: entry[0], scale: entry[1] }; });
  var settings = { initialized: false, primaryCurrency: null, zoneId: null, datePolicy: null };
  // Same schema-2 starter names/IDs as native; only transactions and source events start empty.
  var starterExpenses = [
    ['072989d9-54ad-381e-9674-3ed22f787b7e', 'Продукты'], ['77466f7a-f510-390e-a230-112512b8c1f1', 'Кафе и рестораны'],
    ['e5b1869a-fd3f-3aa4-82e5-657c8d6c3a67', 'Транспорт'], ['39223a4d-d299-34da-af4b-b078e445d858', 'Дом'],
    ['b10831e7-1e00-377e-a3d3-e4d450705520', 'Коммунальные услуги'], ['42b046c0-c562-3997-b50b-d084f5b22c81', 'Здоровье'],
    ['3141bb72-772f-3f44-9840-3921c4406459', 'Покупки'], ['ea87eb9a-8da1-3faa-ae18-f5d88da1d418', 'Развлечения'],
    ['93d5a0b4-a80b-3191-a373-62f9ee4200b3', 'Подписки'], ['dd36e9f5-dc9a-3653-9e42-4221e804d029', 'Образование'],
    ['6902ed5e-cfca-3ba5-a1ad-bdf97d49c367', 'Прочее']
  ];
  var starterIncomes = [
    ['1f46fa63-f36d-3377-869c-228b68f20ea7', 'Зарплата'], ['c575cfbd-d530-3750-ac3f-860ee7892a48', 'Подработка'],
    ['bb6bebe2-1c64-3c9a-98dc-40fef9a89b48', 'Подарки'], ['452f76e5-bca3-33e2-acf0-3795a710e939', 'Прочие доходы']
  ];
  var records = [], categories = starterExpenses.map(function (entry) { return { id: entry[0], revision: 1, name: entry[1], kind: 'expense' }; })
    .concat(starterIncomes.map(function (entry) { return { id: entry[0], revision: 1, name: entry[1], kind: 'income' }; })), imports = [], sources = [];
  var sequence = 0;
  function fail(code, message, field) { var error = new Error(message); error.code = code; error.field = field || null; throw error; }
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function id() {
    if (root.crypto && typeof root.crypto.randomUUID === 'function') return root.crypto.randomUUID();
    sequence += 1;
    return '00000000-0000-4000-8000-' + String(sequence).padStart(12, '0');
  }
  function currency(code) {
    var item = currencies.find(function (entry) { return entry.code === code; });
    if (!item) fail('CURRENCY_UNKNOWN', 'Выберите доступную валюту.', 'currency');
    return item;
  }
  function currentZone() { try { return root.Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch (ignore) { return 'UTC'; } }
  function validZone(value) { try { new root.Intl.DateTimeFormat('en', { timeZone: value }).format(); return true; } catch (ignore) { return false; } }
  function today(zone) {
    var parts = new root.Intl.DateTimeFormat('en-US', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    var values = {};
    parts.forEach(function (part) { values[part.type] = part.value; });
    return values.year.padStart(4, '0') + '-' + values.month + '-' + values.day;
  }
  function date(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) fail('DATE_INVALID', 'Выберите настоящую дату.', 'date');
    var year = Number(value.slice(0, 4)), month = Number(value.slice(5, 7)), day = Number(value.slice(8));
    var leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    var days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1]) fail('DATE_INVALID', 'Выберите настоящую дату.', 'date');
    return value;
  }
  function money(raw, code) {
    var scale = currency(code).scale;
    if (typeof raw !== 'string' || raw.length > 64 || !/^(?:0|[1-9][0-9]{0,23})(?:[.,][0-9]+)?$/.test(raw) || !/[1-9]/.test(raw)) fail('MONEY_INVALID', 'Введите положительную сумму без пробелов, знака и ведущих нулей.', 'amount');
    var parts = raw.replace(',', '.').split('.');
    if ((parts[1] || '').length > scale) fail('MONEY_SCALE', 'Для ' + code + ' допустимо знаков после разделителя: ' + scale + '. Лишние знаки не округляются.', 'amount');
    return { amount: parts[0] + (scale ? '.' + (parts[1] || '').padEnd(scale, '0') : ''), scale: scale };
  }
  function minor(value) { return BigInt(value.replace('.', '')); }
  function decimal(value, scale) {
    var text = value.toString(), negative = text.charAt(0) === '-';
    if (negative) text = text.slice(1);
    text = text.padStart(scale + 1, '0');
    return (negative ? '-' : '') + (scale ? text.slice(0, -scale) + '.' + text.slice(-scale) : text);
  }
  function find(items, target) { var item = items.find(function (entry) { return entry.id === target; }); if (!item) fail('NOT_FOUND', 'Запись уже недоступна.'); return item; }
  function revision(item, expected) { if (!Number.isSafeInteger(expected) || item.revision !== expected) fail('CONFLICT', 'Запись изменилась. Закройте форму и проверьте свежие данные.'); }
  function transaction(payload) {
    if (payload.kind !== 'income' && payload.kind !== 'expense') fail('VALIDATION', 'Выберите вид операции.', 'kind');
    var value = money(payload.amount, payload.currency);
    date(payload.date);
    if (typeof payload.note !== 'string' || payload.note.length > 500) fail('VALIDATION', 'Заметка должна быть не длиннее 500 символов.', 'note');
    if (payload.categoryId !== null) {
      var category = find(categories, payload.categoryId);
      if (category.kind !== payload.kind) fail('REFERENCE_CONFLICT', 'Выберите категорию подходящего вида.', 'categoryId');
    }
    return { kind: payload.kind, date: payload.date, amount: value.amount, currency: payload.currency, scale: value.scale, categoryId: payload.categoryId, note: payload.note };
  }
  function saveTransaction(payload, source) {
    var values = transaction(payload), stamp = new Date().toISOString(), record;
    if (payload.id === null) {
      if (payload.expectedRevision !== null) fail('VALIDATION', 'Новая запись не имеет версии.', 'expectedRevision');
      record = Object.assign({ id: id(), revision: 1, source: source || 'manual', createdAt: stamp, updatedAt: stamp }, values);
      records.push(record);
    } else {
      record = find(records, payload.id); revision(record, payload.expectedRevision);
      Object.assign(record, values, { revision: record.revision + 1, updatedAt: stamp });
    }
    return { record: record };
  }
  function state(payload) {
    var month = payload.month === undefined || payload.month === null ? today(settings.zoneId || currentZone()).slice(0, 7) : payload.month;
    if (typeof month !== 'string' || !/^\d{4}-(?:0[1-9]|1[0-2])$/.test(month) || month.slice(0, 4) === '0000') fail('DATE_INVALID', 'Выберите месяц.', 'month');
    var code = payload.currency === undefined || payload.currency === null ? settings.primaryCurrency : payload.currency;
    var selected = code === null ? null : currency(code);
    var filtered = records.filter(function (record) { return record.date.slice(0, 7) === month && record.currency === code; });
    filtered.sort(function (a, b) { return b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id); });
    var income = BigInt('0'), expense = BigInt('0');
    filtered.forEach(function (record) { if (record.kind === 'income') income += minor(record.amount); else expense += minor(record.amount); });
    return {
      schemaVersion: 2, settings: settings, currencies: currencies, selection: { month: month, currency: code },
      records: filtered, categories: categories, imports: imports.filter(function (candidate) { return candidate.status === 'needs_review'; }), sources: sources,
      summary: selected ? { month: month, currency: code, scale: selected.scale, income: decimal(income, selected.scale), expense: decimal(expense, selected.scale), net: decimal(income - expense, selected.scale) } : null,
      permissions: { notificationAccess: false, cameraGranted: false }, packages: [],
      ocr: { ready: false, languages: ['eng', 'rus', 'kaz'], error: null }, jobs: [], suggestedZoneId: currentZone()
    };
  }
  function execute(action, payload) {
    if (action === 'state') return state(payload);
    if (action === 'settings') {
      currency(payload.primaryCurrency);
      if (typeof payload.zoneId !== 'string' || !validZone(payload.zoneId)) fail('VALIDATION', 'Проверьте часовой пояс ZoneId.', 'zoneId');
      if (payload.datePolicy !== 'POST_TIME_IN_ZONE') fail('VALIDATION', 'Подтвердите правило даты уведомления.', 'datePolicy');
      settings = { initialized: true, primaryCurrency: payload.primaryCurrency, zoneId: payload.zoneId, datePolicy: payload.datePolicy };
      return { settings: settings };
    }
    if (!settings.initialized) fail('SETUP_REQUIRED', 'Сначала выберите валюту и правило даты.');
    if (action === 'scanCamera' || action === 'scanGallery' || action === 'openNotificationAccess') fail('UNAVAILABLE', 'Камера, OCR и системный доступ доступны только в Android-приложении.');
    if (action === 'saveTransaction') return saveTransaction(payload);
    if (action === 'deleteTransaction') {
      var record = find(records, payload.id); revision(record, payload.expectedRevision);
      records.splice(records.indexOf(record), 1); return { id: payload.id, deleted: true };
    }
    if (action === 'saveCategory') {
      if (typeof payload.name !== 'string' || !payload.name.trim() || Array.from(payload.name.trim()).length > 80) fail('VALIDATION', 'Название должно содержать от 1 до 80 символов.', 'name');
      if (payload.kind !== 'income' && payload.kind !== 'expense') fail('VALIDATION', 'Выберите вид категории.', 'kind');
      var category;
      if (payload.id === null) {
        if (payload.expectedRevision !== null) fail('VALIDATION', 'Новая категория не имеет версии.', 'expectedRevision');
        category = { id: id(), revision: 1, name: payload.name.trim(), kind: payload.kind }; categories.push(category);
      } else {
        category = find(categories, payload.id); revision(category, payload.expectedRevision);
        if (category.kind !== payload.kind && records.some(function (entry) { return entry.categoryId === category.id; })) fail('REFERENCE_CONFLICT', 'Вид используемой категории изменить нельзя.', 'kind');
        Object.assign(category, { name: payload.name.trim(), kind: payload.kind, revision: category.revision + 1 });
      }
      return { category: category };
    }
    if (action === 'deleteCategory') {
      var removed = find(categories, payload.id); revision(removed, payload.expectedRevision);
      categories.splice(categories.indexOf(removed), 1);
      records.forEach(function (entry) { if (entry.categoryId === payload.id) { entry.categoryId = null; entry.revision += 1; entry.updatedAt = new Date().toISOString(); } });
      return { id: payload.id, deleted: true };
    }
    if (action === 'saveSources') {
      if (!Array.isArray(payload.packages) || payload.packages.length) fail('VALIDATION', 'Список приложений доступен на Android-устройстве.', 'packages');
      sources = []; return { sources: sources };
    }
    if (action === 'reviewCandidate') {
      var candidate = find(imports, payload.id); revision(candidate, payload.expectedRevision);
      if (candidate.status !== 'needs_review') fail('CONFLICT', 'Материал уже разобран.');
      var linked = null;
      if (payload.decision === 'post') {
        if (candidate.possibleMatchIds.length && payload.confirmDistinct !== true) fail('VALIDATION', 'Подтвердите, что это отдельная операция.', 'confirmDistinct');
        linked = saveTransaction(Object.assign({ id: null, expectedRevision: null }, payload.transaction), candidate.source).record;
      } else if (payload.decision === 'link') linked = find(records, payload.transactionId);
      else if (payload.decision !== 'ignore') fail('VALIDATION', 'Выберите решение.', 'decision');
      candidate.status = payload.decision === 'post' ? 'posted' : payload.decision === 'link' ? 'linked' : 'ignored';
      candidate.transactionId = linked ? linked.id : null; candidate.revision += 1;
      return { candidate: candidate, record: linked };
    }
    fail('UNKNOWN_ACTION', 'Неизвестное действие.');
  }
  root.PreviewAdapter = { request: function (action, payload) { return Promise.resolve().then(function () { return clone(execute(action, payload || {})); }); } };
}(window));
