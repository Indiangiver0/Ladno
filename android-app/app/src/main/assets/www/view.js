(function (root) {
  'use strict';

  // This module only presents native DTOs. app.js owns events, drafts and the bridge.
  var document = root.document;
  var paths = {
    records: ['M5 4h14v17H5z', 'M9 1v6M15 1v6M8 11h8M8 15h5'],
    charts: ['M12 3v9h9', 'M8 3.9a9 9 0 1 0 12.1 12.1', 'M16 3.9a9 9 0 0 1 4.1 4.1'],
    categories: ['M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z'],
    imports: ['M5 3h14v18l-3-2-4 2-4-2-3 2z', 'M8 7h8M8 11h8M8 15h4'],
    settings: ['M4 6h16M4 18h16M9 3v6M15 15v6M4 12h16'],
    plus: ['M12 5v14M5 12h14'],
    down: ['M7 7l10 10M7 17h10V7'],
    up: ['M7 17L17 7M7 7h10v10'],
    close: ['M6 6l12 12M6 18L18 6'],
    edit: ['M4 16l12-12 4 4L8 20H4zM13 7l4 4'],
    trash: ['M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7'],
    camera: ['M3 7h4l2-3h6l2 3h4v14H3z', 'M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0'],
    gallery: ['M3 3h18v18H3zM3 17l6-6 4 4 3-3 5 5', 'M9 7h.01'],
    info: ['M12 10v7M12 6v.01', 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0'],
    check: ['M5 12l4 4L19 6'],
    leaf: ['M20 4C10 4 4 7 4 13a7 7 0 0 0 7 7c6 0 9-6 9-16zM4 20L15 9'],
    empty: ['M4 8l8-5 8 5v12H4zM4 12h5l1 3h4l1-3h5'],
    bell: ['M5 17h14l-2-4V9a5 5 0 0 0-10 0v4zM10 21h4'],
    lock: ['M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5zM12 14v3'],
    error: ['M12 3l10 18H2zM12 9v5M12 17v.01']
  };
  var tabs = [
    { id: 'records', label: 'Операции' },
    { id: 'charts', label: 'Графики' },
    { id: 'categories', label: 'Категории' },
    { id: 'imports', label: 'Разбор' },
    { id: 'settings', label: 'Настройки' }
  ];
  var monthNames = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'];
  var reasons = {
    UNKNOWN_KIND: 'Не удалось определить доход или расход.',
    AMOUNT_MISSING: 'Сумма отсутствует или скрыта источником.',
    CURRENCY_MISSING: 'Нужно выбрать валюту операции.',
    CURRENCY_UNKNOWN: 'Валюта источника не поддерживается.',
    DATE_MISSING: 'Нужно указать дату операции.',
    AMBIGUOUS_TOTAL: 'Найдено несколько возможных итоговых сумм.',
    AMBIGUOUS_SEPARATOR: 'Неясно, как прочитать разделитель в сумме.',
    UNSUPPORTED_EVENT: 'Сообщение может быть переводом, возвратом или незавершённой операцией.',
    POSSIBLE_DUPLICATE: 'В учёте есть возможное совпадение. Проверьте перед добавлением.',
    IDENTITY_UNCERTAIN: 'Не удалось надёжно установить самостоятельность операции.',
    LOW_CONFIDENCE: 'Результат распознавания нужно проверить.',
    LIMIT_EXCEEDED: 'Источник превышает технический предел обработки.',
    MONEY_INVALID: 'Сумму нужно проверить.',
    MONEY_SCALE: 'Точность суммы не соответствует валюте.',
    DATE_INVALID: 'Дата источника требует проверки.',
    OFD_UNAVAILABLE: 'ОФД недоступен. Проверьте поля, взятые из QR.',
    OFD_INVALID_RESPONSE: 'Не удалось проверить ответ ОФД. Проверьте чек вручную.',
    OFD_MISMATCH: 'Данные ОФД не совпали с QR. Требуется проверка.',
    OFD_REVIEW_REQUIRED: 'Проверьте вид операции и статус чека ОФД.'
  };
  var errorMessages = {
    CONFLICT: 'Запись уже изменилась. Проверьте свежую версию; ваш ввод сохранён.',
    REFERENCE_CONFLICT: 'Категория используется в операциях. Её вид сейчас изменить нельзя.',
    STORAGE_FAILED: 'Не удалось сохранить данные. Ваш ввод остаётся в форме.',
    BUSY: 'Другая операция ещё выполняется. Попробуйте через несколько секунд.',
    OCR_UNAVAILABLE: 'Распознавание сейчас недоступно. Можно добавить операцию вручную.',
    PERMISSION_DENIED: 'Системный доступ не предоставлен.',
    ACTIVITY_UNAVAILABLE: 'На устройстве нет доступного приложения для этого действия.',
    IMAGE_INVALID: 'Не удалось прочитать изображение. Выберите другой снимок.',
    UNAVAILABLE: 'Это действие доступно в Android-приложении.',
    INTERNAL: 'Не удалось выполнить действие. Попробуйте ещё раз.'
  };

  function node(tag, className, text) {
    var result = document.createElement(tag);
    if (className) result.className = className;
    if (text !== undefined && text !== null) result.textContent = String(text);
    return result;
  }
  function append(parent) {
    for (var i = 1; i < arguments.length; i += 1) {
      if (arguments[i]) parent.appendChild(arguments[i]);
    }
    return parent;
  }
  function clear(parent) {
    while (parent.firstChild) parent.removeChild(parent.firstChild);
  }
  function attr(element, name, value) {
    if (value !== undefined && value !== null) element.setAttribute(name, String(value));
    return element;
  }
  function icon(name, size) {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    var attributes = { width: size || 20, height: size || 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.6', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true', focusable: 'false' };
    Object.keys(attributes).forEach(function (key) { attr(svg, key, attributes[key]); });
    (paths[name] || paths.info).forEach(function (data) {
      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', data);
      svg.appendChild(path);
    });
    return svg;
  }
  function action(name, label, options) {
    var settings = options || {};
    var button = node('button', settings.className || 'button text');
    button.type = 'button';
    attr(button, 'data-action', name);
    attr(button, 'data-id', settings.id);
    attr(button, 'data-tab', settings.tab);
    if (settings.ariaLabel) attr(button, 'aria-label', settings.ariaLabel);
    if (settings.title) button.title = settings.title;
    if (settings.icon) button.appendChild(icon(settings.icon, settings.iconSize));
    if (label) button.appendChild(node('span', '', label));
    button.disabled = !!settings.disabled;
    return button;
  }
  function submit(label, busy) {
    var button = node('button', 'button primary', busy ? 'Подождите…' : label);
    button.type = 'submit';
    button.disabled = !!busy;
    return button;
  }
  function pill(label, kind) { return node('span', 'pill' + (kind ? ' ' + kind : ''), label); }
  function array(value) { return Array.isArray(value) ? value : []; }
  function findById(items, id) {
    for (var i = 0; i < items.length; i += 1) if (items[i].id === id) return items[i];
    return null;
  }
  function kindName(kind) { return kind === 'income' ? 'Доход' : kind === 'expense' ? 'Расход' : 'Вид не определён'; }
  function sourceName(source) { return source === 'receipt' ? 'Чек' : source === 'notification' ? 'Уведомление' : 'Вручную'; }
  function dateLabel(value) {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.slice(8) + '.' + value.slice(5, 7) + '.' + value.slice(0, 4) : 'Дата не определена';
  }
  function monthLabel(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}$/.test(value)) return 'Выбранный месяц';
    var months = { '01': 0, '02': 1, '03': 2, '04': 3, '05': 4, '06': 5, '07': 6, '08': 7, '09': 8, '10': 9, '11': 10, '12': 11 };
    return (monthNames[months[value.slice(5)]] || value.slice(5)) + ' ' + value.slice(0, 4);
  }
  function formattedAmount(value) {
    if (typeof value !== 'string' || !/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value)) return '—';
    var negative = value.charAt(0) === '-';
    var parts = (negative ? value.slice(1) : value).split('.');
    return (negative ? '−' : '') + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, '\u202f') + (parts.length > 1 ? ',' + parts[1] : '');
  }
  function money(value, currency, className, prefix) {
    var container = node('p', className || 'metric-amount');
    append(container, node('span', 'money-value', (prefix || '') + formattedAmount(value)));
    if (currency) append(container, node('span', 'currency', currency));
    return container;
  }
  function currencyInfo(state, code) {
    var currencies = array(state.currencies);
    for (var i = 0; i < currencies.length; i += 1) if (currencies[i].code === code) return currencies[i];
    return null;
  }
  function errorText(error) {
    return errorMessages[error && error.code] || (error && error.message) || 'Не удалось выполнить действие. Проверьте ввод и попробуйте снова.';
  }
  function errorNotice(error, inline, title) {
    if (!error) return null;
    var result = node('div', inline ? 'form-inline-error' : 'notice error');
    attr(result, 'role', 'alert');
    if (!inline) result.appendChild(icon('error', 19));
    if (title) {
      result.appendChild(append(node('span'), node('strong', 'notice-title', title), node('span', '', error.message || errorText(error))));
    } else result.appendChild(node('span', '', errorText(error)));
    return result;
  }
  function note(text) { return append(node('p', 'footnote'), icon('info', 15), node('span', '', text)); }
  function topbar() {
    return append(node('div', 'topbar'), append(node('div', 'brand'), node('span', 'brand-mark', 'л'), node('span', '', 'Ладно')), append(node('span', 'local-label'), node('span', 'local-dot'), node('span', '', 'На устройстве')));
  }
  function previewNotice(ui) {
    return ui.preview ? node('div', 'preview-note', 'Предпросмотр в браузере · данные только в памяти этой вкладки. Камера, QR, OCR и доступ к уведомлениям доступны в Android-приложении.') : null;
  }
  function heading(title, description, index) {
    return append(node('header', 'page-heading'), append(node('div'), node('h1', '', title), node('p', '', description)), attr(node('span', 'page-index', index), 'aria-hidden', 'true'));
  }
  function empty(title, description, button, symbol) {
    return append(node('div', 'empty-state'), append(node('div', 'empty-symbol'), icon(symbol || 'empty', 27)), node('h2', '', title), node('p', '', description), button);
  }
  function form(type, target) {
    var result = node('form', 'form-stack');
    attr(result, 'data-form', type);
    attr(result, 'data-id', target && target.id ? target.id : '');
    result.noValidate = false;
    return result;
  }
  function input(name, type, value) {
    var element = node('input');
    element.name = name;
    element.type = type || 'text';
    element.value = value === undefined || value === null ? '' : String(value);
    return element;
  }
  function hidden(name, value) { return input(name, 'hidden', value); }
  function identityFields(target) {
    var fragment = document.createDocumentFragment();
    append(fragment, hidden('id', target ? target.id : ''), hidden('expectedRevision', target ? target.revision : ''));
    return fragment;
  }
  function field(label, control, help, className) {
    var result = node('label', 'field' + (className ? ' ' + className : ''));
    append(result, node('span', 'field-label', label), control, help ? node('small', '', help) : null);
    return result;
  }
  function option(value, label) {
    var result = node('option', '', label);
    result.value = value === null ? '' : String(value);
    return result;
  }
  function select(name, values, value, placeholder) {
    var result = node('select');
    result.name = name;
    if (placeholder) result.appendChild(option('', placeholder));
    values.forEach(function (item) { result.appendChild(option(item.value, item.label)); });
    result.value = value === undefined || value === null ? '' : String(value);
    return result;
  }
  function currencySelect(state, name, value, placeholder) {
    return select(name, array(state.currencies).map(function (item) { return { value: item.code, label: item.code + ' · ' + item.scale + ' знаков' }; }), value, placeholder);
  }
  function check(name, value, checked, title, help, className) {
    var control = input(name, 'checkbox', value);
    control.checked = !!checked;
    return append(node('label', 'checkbox-field' + (className ? ' ' + className : '')), control, append(node('span', '', title), help ? node('small', '', help) : null));
  }
  function settingsForm(state, ui, initial) {
    var settings = state.settings || {};
    var result = form('settings');
    var currency = currencySelect(state, 'primaryCurrency', settings.primaryCurrency, 'Выберите валюту');
    currency.required = true;
    var zone = input('zoneId', 'text', settings.zoneId || state.suggestedZoneId || '');
    zone.required = true;
    zone.maxLength = 100;
    zone.autocomplete = 'off';
    zone.spellcheck = false;
    attr(zone, 'autocapitalize', 'none');
    var datePolicy = check('datePolicy', 'POST_TIME_IN_ZONE', settings.datePolicy === 'POST_TIME_IN_ZONE', 'Если в уведомлении нет даты банка, использовать дату его отправки в выбранном часовом поясе.', 'Дата чека без даты не угадывается. Сохранённые даты не сдвигаются при смене часового пояса.');
    datePolicy.querySelector('input').required = true;
    append(result, field('Основная валюта', currency, 'Разные валюты учитываются отдельно, без конвертации.'), field('Часовой пояс', zone, 'Предложен пояс устройства. Проверьте его или введите ZoneId, например Asia/Qyzylorda.'), datePolicy);
    if (!initial) result.appendChild(node('p', 'small muted', 'Изменение основной валюты и пояса не пересчитывает сохранённые суммы и даты.'));
    append(result, errorNotice(ui.error, true), submit(initial ? 'Начать учёт' : 'Сохранить настройки', ui.busy));
    return result;
  }
  function setup(state, ui) {
    var main = node('main', 'setup-shell');
    append(main, topbar(), previewNotice(ui), append(node('div', 'setup-illustration'), icon('leaf', 38)), node('p', 'eyebrow', 'Первый маленький шаг'), node('h1', 'setup-title', 'Деньги — яснее. Жизнь — спокойнее.'), node('p', 'setup-description', 'Выберите, как вести личный учёт. Начнём с чистого листа: операций и подключённых источников пока нет.'));
    append(main, append(node('section', 'card setup-card'), settingsForm(state, ui, true)), note('Записи хранятся в приложении на этом устройстве. При удалении приложения или потере телефона учёт может быть утрачен.'));
    return main;
  }
  function selectionBar(state, ui) {
    var selection = state.selection || {};
    var result = node('div', 'selection-bar');
    var month = input('month', 'month', selection.month);
    month.required = true;
    month.min = '0001-01';
    month.max = '9999-12';
    attr(month, 'data-action', 'changeSelection');
    attr(month, 'aria-label', 'Месяц операций');
    var currency = currencySelect(state, 'currency', selection.currency, 'Валюта');
    attr(currency, 'data-action', 'changeSelection');
    attr(currency, 'aria-label', 'Валюта операций');
    month.disabled = !!ui.busy;
    currency.disabled = !!ui.busy;
    append(result, append(node('label'), node('span', 'filter-label', 'Месяц'), month), append(node('label'), node('span', 'filter-label', 'Валюта'), currency));
    return result;
  }
  function summary(state) {
    var value = state.summary;
    if (!value) return append(node('section', 'card'), node('p', 'muted', 'Итоги пока недоступны.'));
    var hero = node('section', 'summary-hero');
    attr(hero, 'aria-label', 'Итоги выбранного месяца');
    append(hero, append(node('div', 'summary-top'), node('p', 'eyebrow', 'Доходы − расходы'), pill('За месяц')), money(value.net, value.currency, 'summary-amount'), node('p', 'summary-caption', monthLabel(value.month) + ' · по дате операции. Это результат учёта за период, не баланс банка.'));
    var income = append(node('section', 'metric'), append(node('div', 'metric-top'), icon('down', 16), node('span', '', 'Доходы')), money(value.income, value.currency));
    var expense = append(node('section', 'metric out'), append(node('div', 'metric-top'), icon('up', 16), node('span', '', 'Расходы')), money(value.expense, value.currency));
    return append(document.createDocumentFragment(), hero, append(node('div', 'summary-pair'), income, expense));
  }
  function recordRow(record, state, ui) {
    var income = record.kind === 'income';
    var category = findById(array(state.categories), record.categoryId);
    var title = category ? category.name : 'Без категории';
    var row = node('li', 'record');
    attr(row, 'data-record-id', record.id);
    var detail = append(node('div', 'record-detail'), node('p', 'record-title', title), node('p', 'record-meta', dateLabel(record.date) + ' · ' + sourceName(record.source)));
    append(row, append(node('div', 'record-top'), append(node('span', 'record-symbol' + (income ? ' income' : '')), icon(income ? 'down' : 'up', 19)), detail, money(record.amount, record.currency, 'record-money' + (income ? ' income' : ''), income ? '+ ' : '− ')));
    if (record.note) row.appendChild(node('p', 'record-note', record.note));
    append(row, append(node('div', 'record-actions'), action('editTransaction', 'Изменить', { id: record.id, icon: 'edit', iconSize: 13, disabled: ui.busy }), action('deleteTransaction', 'Удалить', { id: record.id, className: 'button text danger-text', disabled: ui.busy })));
    return row;
  }
  function recordsPage(state, ui) {
    var result = node('div');
    append(result, selectionBar(state, ui), summary(state), append(node('div', 'quick-actions'), action('newExpense', 'Расход', { className: 'button primary', icon: 'plus', disabled: ui.busy }), action('newIncome', 'Доход', { className: 'button secondary', icon: 'plus', disabled: ui.busy })));
    append(result, action('changeTab', 'Графики расходов', { tab: 'charts', className: 'button secondary full charts-link', icon: 'charts', disabled: ui.busy }), jobs(state, ui, true));
    var records = array(state.records);
    var section = node('section', 'card records-section');
    append(section, append(node('div', 'section-heading'), node('h2', '', 'История операций'), pill(String(records.length), 'soft')));
    if (records.length) {
      var list = node('ul', 'records-list');
      records.forEach(function (record) { list.appendChild(recordRow(record, state, ui)); });
      section.appendChild(list);
    } else {
      section.appendChild(empty('Пока чистый лист', 'За этот месяц в выбранной валюте операций нет. Добавьте первую или распознайте чек в разделе «Разбор».', action('newExpense', 'Добавить расход', { className: 'button soft', icon: 'plus', disabled: ui.busy })));
    }
    append(result, section, note('В итогах только сохранённые операции. Записи без категории учитываются полностью; материалы на разборе в суммы не входят.'));
    return result;
  }
  // Display-only breakdown. The native summary remains authoritative; no money is converted to Number.
  function chartModel(state) {
    try {
      if (typeof root.BigInt !== 'function') throw new Error('Exact arithmetic unavailable');
      var B = root.BigInt, zero = B('0'), one = B('1'), basis = B('10000'), units = B('1000000000');
      var selection = state.selection || {}, summaryValue = state.summary;
      var scale = summaryValue && summaryValue.scale;
      if (!summaryValue || !Number.isInteger(scale) || scale < 0 || scale > 6 || !/^[A-Z]{3}$/.test(selection.currency || '')
          || !/^[0-9]{4}-(?:0[1-9]|1[0-2])$/.test(selection.month || '') || selection.month.slice(0, 4) === '0000'
          || summaryValue.month !== selection.month || summaryValue.currency !== selection.currency) throw new Error('Selection mismatch');
      var info = currencyInfo(state, selection.currency);
      if (!info || info.scale !== scale || !Array.isArray(state.records) || !Array.isArray(state.categories)) throw new Error('Currency mismatch');
      function readMinor(value, positive, inputLimit) {
        if (typeof value !== 'string') throw new Error('Amount missing');
        var expression = scale ? '^(?:0|[1-9][0-9]*)\\.[0-9]{' + scale + '}$' : '^(?:0|[1-9][0-9]*)$';
        if (!new RegExp(expression).test(value) || (inputLimit && value.split('.')[0].length > 24)) throw new Error('Noncanonical amount');
        var result = B(value.replace('.', ''));
        if (positive && result <= zero) throw new Error('Nonpositive amount');
        return result;
      }
      function decimal(value) {
        var text = value.toString().padStart(scale + 1, '0');
        return scale ? text.slice(0, -scale) + '.' + text.slice(-scale) : text;
      }
      function percentage(value) { return String(Math.floor(value / 100)) + '.' + String(value % 100).padStart(2, '0'); }
      function compareKey(a, b) { return a.key < b.key ? -1 : a.key > b.key ? 1 : 0; }
      var categories = Object.create(null), names = Object.create(null), seen = Object.create(null), grouped = Object.create(null), included = [], total = zero;
      state.categories.forEach(function (category) {
        if (!category || typeof category.id !== 'string' || categories[category.id]) throw new Error('Category mismatch');
        categories[category.id] = category;
        names[category.name] = (names[category.name] || 0) + 1;
      });
      state.records.forEach(function (record) {
        if (!record || typeof record.id !== 'string' || seen[record.id] || record.currency !== selection.currency || record.scale !== scale
            || (record.kind !== 'expense' && record.kind !== 'income') || typeof record.date !== 'string'
            || !/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(record.date) || record.date.slice(0, 7) !== selection.month || record.deleted) throw new Error('Record mismatch');
        seen[record.id] = true;
        var day = Number(record.date.slice(8)), year = Number(record.date.slice(0, 4)), month = Number(record.date.slice(5, 7));
        var days = [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
        if (day < 1 || day > days[month - 1]) throw new Error('Invalid date');
        var value = readMinor(record.amount, true, true), category = record.categoryId === null ? null : categories[record.categoryId];
        if (record.categoryId !== null && !category) throw new Error('Category missing');
        if (record.kind !== 'expense') return;
        if (category && category.kind !== 'expense') throw new Error('Category kind mismatch');
        var key = category ? category.id : '~uncategorized';
        if (!grouped[key]) grouped[key] = { key: key, categoryId: category ? category.id : null, name: category ? category.name : 'Без категории', label: category ? category.name + (names[category.name] > 1 ? ' · ' + category.id : '') : 'Без категории', value: zero };
        grouped[key].value += value; total += value; included.push(record.id);
      });
      if (total !== readMinor(summaryValue.expense, false, false)) throw new Error('Summary mismatch');
      var groups = Object.keys(grouped).map(function (key) { return grouped[key]; });
      groups.sort(function (a, b) { return a.value > b.value ? -1 : a.value < b.value ? 1 : compareKey(a, b); });
      var used = zero;
      groups.forEach(function (group) { group.points = group.value * basis / total; group.remainder = group.value * basis % total; used += group.points; });
      var ranking = groups.slice().sort(function (a, b) { return a.remainder > b.remainder ? -1 : a.remainder < b.remainder ? 1 : compareKey(a, b); });
      var left = total === zero ? zero : basis - used;
      ranking.forEach(function (group) { if (left > zero) { group.points += one; left -= one; } });
      var sectors = groups.length > 6 ? groups.slice(0, 5) : groups.slice();
      if (groups.length > 6) {
        var other = { key: '~other', name: 'Остальные', label: 'Остальные', value: zero, points: zero, memberKeys: [] };
        groups.slice(5).forEach(function (group) { other.value += group.value; other.points += group.points; other.memberKeys.push(group.key); });
        sectors.push(other);
      }
      var cumulative = zero, previous = 0;
      sectors = sectors.map(function (group, index) {
        cumulative += group.value;
        var end = Number(units * cumulative / total), points = Number(group.points);
        var sector = { key: group.key, name: group.name, label: group.label, memberKeys: group.memberKeys || [group.key], minor: group.value.toString(), amount: decimal(group.value), basisPoints: points, percent: percentage(points), startUnit: previous, endUnit: end, widthUnits: end - previous, zeroWidth: end === previous, colorIndex: index };
        previous = end; return sector;
      });
      groups = groups.map(function (group, index) {
        var points = Number(group.points);
        return { key: group.key, categoryId: group.categoryId, name: group.name, label: group.label, minor: group.value.toString(), amount: decimal(group.value), basisPoints: points, percent: percentage(points), remainderNumerator: group.remainder.toString(), colorIndex: index < 5 || sectors.length === groups.length ? index : 5, inOthers: sectors.length !== groups.length && index >= 5 };
      });
      return { status: total === zero ? 'empty' : 'ready', chartEmpty: total === zero, totalMinor: total.toString(), totalAmount: decimal(total), currency: selection.currency, month: selection.month, scale: scale, includedExpenseIds: included.sort(), groups: groups, sectors: sectors };
    } catch (error) { return { status: 'unavailable', chartStatus: 'unavailable', message: 'Не удалось построить график', noPartialTotals: true }; }
  }
  function pieChart(model) {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    attr(svg, 'viewBox', '0 0 240 240'); attr(svg, 'class', 'expense-pie'); attr(svg, 'role', 'img');
    attr(svg, 'aria-label', 'Распределение расходов по категориям. Точные суммы и доли приведены ниже.');
    model.sectors.forEach(function (sector, index) {
      if (sector.zeroWidth) return;
      var full = sector.widthUnits === 1000000000;
      var shape = document.createElementNS('http://www.w3.org/2000/svg', full ? 'circle' : 'path');
      if (full) { attr(shape, 'cx', '120'); attr(shape, 'cy', '120'); attr(shape, 'r', '108'); }
      else {
        var start = Math.PI * 2 * sector.startUnit / 1000000000 - Math.PI / 2;
        var end = Math.PI * 2 * sector.endUnit / 1000000000 - Math.PI / 2;
        attr(shape, 'd', 'M120 120 L' + (120 + 108 * Math.cos(start)) + ' ' + (120 + 108 * Math.sin(start)) + ' A108 108 0 ' + (sector.widthUnits > 500000000 ? '1' : '0') + ' 1 ' + (120 + 108 * Math.cos(end)) + ' ' + (120 + 108 * Math.sin(end)) + ' Z');
      }
      attr(shape, 'class', 'pie-sector chart-color-' + index);
      var title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
      title.textContent = sector.label + ': ' + formattedAmount(sector.amount) + ' ' + model.currency + ' · ' + sector.percent.replace('.', ',') + '%';
      shape.appendChild(title); svg.appendChild(shape);
      if (sector.widthUnits >= 80000000) {
        var angle = Math.PI * (sector.startUnit + sector.endUnit) / 1000000000 - Math.PI / 2;
        var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        attr(label, 'x', full ? 120 : 120 + 70 * Math.cos(angle)); attr(label, 'y', full ? 120 : 120 + 70 * Math.sin(angle)); attr(label, 'class', 'pie-label'); attr(label, 'aria-hidden', 'true');
        label.textContent = sector.percent.replace('.', ',') + '%'; svg.appendChild(label);
      }
    });
    return svg;
  }
  function chartsPage(state, ui) {
    var result = node('div'), model = chartModel(state);
    append(result, selectionBar(state, ui));
    if (model.status === 'unavailable') return append(result, append(node('section', 'card'), node('h2', '', model.message), node('p', 'small muted chart-description', 'Операции доступны в учёте. Попробуйте обновить экран; неполный разрез не показывается.'), action('changeTab', 'К операциям', { tab: 'records', className: 'button soft' })));
    var section = node('section', 'card chart-card');
    append(section, node('p', 'eyebrow', monthLabel(model.month) + ' · ' + model.currency), node('h2', 'chart-title', 'Расходы по категориям'), money(model.totalAmount, model.currency, 'chart-total'));
    if (model.chartEmpty) return append(result, append(section, empty('За этот месяц расходов нет', 'Добавленные расходы появятся здесь, в том числе без категории.', action('newExpense', 'Добавить расход', { className: 'button soft', icon: 'plus', disabled: ui.busy }), 'charts')));
    append(section, pieChart(model));
    var other = model.sectors.find(function (sector) { return sector.key === '~other'; });
    if (other) append(section, node('p', 'small muted chart-description', '«Остальные» на круге: ' + formattedAmount(other.amount) + ' ' + model.currency + ' · ' + other.percent.replace('.', ',') + '%. Ниже — все категории отдельно.'));
    var list = node('ul', 'chart-legend'); attr(list, 'aria-label', 'Все категории расходов, суммы и доли');
    model.groups.forEach(function (group, index) {
      var text = append(node('div', 'chart-category'), node('strong', '', group.label), group.inOthers ? node('small', '', 'В секторе «Остальные»') : null);
      var figures = append(node('div', 'chart-figures'), money(group.amount, model.currency, 'chart-money'), node('p', 'chart-percent', group.percent.replace('.', ',') + '%'), group.basisPoints === 0 ? node('small', 'muted', 'менее 0,01%') : null);
      append(list, append(node('li', 'chart-legend-row'), attr(node('span', 'chart-swatch chart-color-' + group.colorIndex), 'aria-hidden', 'true'), text, figures));
    });
    append(section, list); append(result, section, note('Только сохранённые расходы выбранного месяца и валюты. Доходы и чеки на разборе сюда не входят. Малые доли могут быть неразличимы на круге; точные суммы указаны в списке.'));
    return result;
  }
  function categoriesPage(state, ui) {
    var result = node('div');
    append(result, action('newCategory', 'Новая категория', { className: 'button primary full', icon: 'plus', disabled: ui.busy }));
    var categories = array(state.categories);
    ['expense', 'income'].forEach(function (kind) {
      var entries = categories.filter(function (item) { return item.kind === kind; });
      var section = node('section', 'card category-group');
      append(section, append(node('div', 'section-heading'), node('h2', '', kind === 'income' ? 'Для доходов' : 'Для расходов'), pill(String(entries.length), 'soft')));
      if (!entries.length) section.appendChild(node('p', 'small muted', 'Категорий пока нет. Операции можно сохранять и без них.'));
      var list = node('ul', 'category-list');
      entries.forEach(function (category) {
        var edit = action('editCategory', '', { id: category.id, icon: 'edit', iconSize: 17, className: 'button text icon-only', ariaLabel: 'Изменить категорию ' + category.name, disabled: ui.busy });
        var remove = action('deleteCategory', '', { id: category.id, icon: 'trash', iconSize: 17, className: 'button text icon-only danger-text', ariaLabel: 'Удалить категорию ' + category.name, disabled: ui.busy });
        append(list, append(node('li', 'category-row'), node('span', 'category-dot ' + kind), node('span', 'category-name', category.name), append(node('div', 'category-actions'), edit, remove)));
      });
      append(section, list);
      result.appendChild(section);
    });
    result.appendChild(note('При удалении категории её операции останутся в учёте как «Без категории». Суммы и даты сохранятся.'));
    return result;
  }
  function reasonList(candidate) {
    var list = node('ul', 'reason-list');
    array(candidate.reasons).forEach(function (code) { list.appendChild(node('li', '', reasons[code] || 'Требуется ручная проверка (' + code + ').')); });
    if (!list.firstChild) list.appendChild(node('li', '', 'Проверьте распознанные поля перед добавлением.'));
    return list;
  }
  function jobs(state, ui, latestOnly) {
    var entries = array(state.jobs);
    if (latestOnly) entries = entries.filter(function (job) { return job.type === 'scanCamera' || job.type === 'scanGallery'; }).slice(-1);
    if (!entries.length) return null;
    var section = node('section', 'card scan-results');
    attr(section, 'aria-label', 'Результат обработки');
    append(section, append(node('div', 'section-heading'), node('h2', '', latestOnly ? 'Последний скан' : 'Обработка')));
    var typeNames = { scanCamera: 'Чек с камеры', scanGallery: 'Чек из изображения', openNotificationAccess: 'Доступ к уведомлениям' };
    var statusNames = { queued: 'В очереди', running: 'Выполняется', completed: 'Завершено', cancelled: 'Отменено', failed: 'Не выполнено' };
    entries.slice(-8).reverse().forEach(function (job) {
      var active = job.status === 'queued' || job.status === 'running';
      var row = node('div', 'job' + (active ? ' running' : '') + (job.status === 'failed' ? ' error' : ''));
      append(row, append(node('div', 'job-title'), node('span', 'job-indicator'), node('span', '', (typeNames[job.type] || 'Обработка') + ' · ' + (statusNames[job.status] || job.status))));
      if (job.error) row.appendChild(node('p', '', errorText(job.error)));
      else if (job.status === 'completed') {
        var outcome = job.outcome;
        var outcomeMessages = { posted: 'Расход добавлен автоматически.', needs_review: 'Расход пока не добавлен. Проверьте распознанные поля.', linked: 'Чек связан с учтённой операцией. Второй расход не добавлен.', duplicate: 'Этот снимок уже обработан. Второй расход не добавлен.', ignored: 'Чек убран из разбора. Расход не добавлен.' };
        var pending = outcome ? findById(array(state.imports), outcome.candidateId) : null;
        var canReview = pending && pending.status === 'needs_review';
        var resolvedReview = outcome && outcome.status === 'needs_review' && !canReview;
        row.appendChild(node('p', '', job.type === 'openNotificationAccess' ? 'Актуальный доступ показан в настройках приложения.' : resolvedReview ? 'Этот чек уже разобран или убран из списка. Сохранённые расходы доступны в операциях.' : outcomeMessages[outcome && outcome.status] || 'Обработка закончена. Проверьте операции и список разбора.'));
        if (outcome) {
          if (canReview || outcome.transactionId) append(row, action('viewScanOutcome', canReview ? 'Проверить чек' : 'Посмотреть операцию', { id: job.jobId, className: 'button soft scan-result-action', disabled: ui && ui.busy }));
        }
      }
      else if (job.status === 'cancelled') row.appendChild(node('p', '', 'Сканирование отменено. Расход не добавлен.'));
      else if (active) row.appendChild(node('p', '', 'Можно оставить этот экран. Готовый результат появится после обработки.'));
      section.appendChild(row);
    });
    return section;
  }
  function importCard(candidate, ui) {
    var result = node('article', 'card import-card');
    var title = candidate.sourceLabel || sourceName(candidate.source);
    append(result, append(node('div', 'import-heading'), node('h3', 'import-source', title), pill('Проверить', 'amber')), money(candidate.amount, candidate.currency, 'import-money'), append(node('div', 'import-fields'), node('span', '', kindName(candidate.kind)), node('span', '', dateLabel(candidate.date)), node('span', '', sourceName(candidate.source))));
    if (candidate.note) result.appendChild(node('p', 'small muted', candidate.note));
    attr(result, 'data-import-id', candidate.id);
    append(result, reasonList(candidate), append(node('div', 'import-actions'), action('reviewCandidate', 'Проверить поля', { id: candidate.id, className: 'button soft', disabled: ui.busy }), candidate.source === 'receipt' ? action('dismissImport', 'Убрать', { id: candidate.id, className: 'button secondary', icon: 'trash', iconSize: 16, ariaLabel: 'Убрать неучтённый чек из разбора', disabled: ui.busy }) : null));
    return result;
  }
  function importsPage(state, ui) {
    var result = node('div');
    var ocr = state.ocr || {};
    var permissions = state.permissions || {};
    append(result, errorNotice(permissions.notificationError, false, 'Обработка уведомлений недоступна'));
    var scanDisabled = !!ui.busy || !!ui.preview;
    var capture = node('section');
    var camera = action('scanCamera', '', { className: 'capture-card', disabled: scanDisabled, icon: 'camera', iconSize: 25 });
    append(camera, append(node('span', '', 'Снять чек'), node('small', '', 'Открыть камеру')));
    var gallery = action('scanGallery', '', { className: 'capture-card', disabled: scanDisabled, icon: 'gallery', iconSize: 25 });
    append(gallery, append(node('span', '', 'Изображение'), node('small', '', 'Выбрать снимок чека')));
    append(capture, append(node('div', 'capture-grid'), camera, gallery));
    if (ocr.error) append(capture, errorNotice(ocr.error, false));
    else if (!ocr.ready && !ui.preview) append(capture, node('p', 'footnote', 'Модуль распознавания ещё не готов. Доступность обновится автоматически.'));
    append(capture, node('p', 'capture-hint', 'Снимите один QR фискального чека крупно и без бликов. Поддерживаемые QR ОФД «Казахтелеком» проверяются через интернет. Подтверждённая завершённая покупка после проверки повторов может автоматически попасть в расходы. Другие QR, возвраты и неподтверждённые данные потребуют проверки. Если QR нет, снимите чек целиком: нужны итог, валюта и дата.'));
    append(result, capture, jobs(state, ui));
    var imports = array(state.imports).filter(function (item) { return item.status === 'needs_review'; });
    append(result, append(node('div', 'section-heading'), node('h2', '', 'Нужно ваше решение'), pill(String(imports.length), imports.length ? 'amber' : 'soft')));
    if (imports.length) imports.forEach(function (candidate) { result.appendChild(importCard(candidate, ui)); });
    else result.appendChild(append(node('section', 'card'), empty('Всё разобрано', 'Здесь появятся чеки и уведомления, в которых нужно уточнить сумму, дату или возможное совпадение.', null, 'check')));
    var help = append(node('section', 'card'), append(node('div', 'section-heading'), node('h2', '', 'Уведомления банков'), icon('bell', 19)), node('p', 'small muted', 'Выберите приложения-источники и включите системный доступ в настройках. Поддержка зависит от текста уведомления: скрытые суммы и неизвестные сообщения требуют разбора.'), action('changeTab', 'Настроить источники', { tab: 'settings', icon: 'settings' }));
    append(result, help, note('Изображение обрабатывается на устройстве и не отправляется. Для проверки официальному ОФД передаются только реквизиты QR. В учёте остаются структурированные поля; исходный текст уведомления и полный текст OCR здесь не хранятся.'));
    return result;
  }
  function statusLine(label, value, warn) {
    return append(node('div', 'status-line'), node('span', '', label), node('strong', warn ? 'warn' : '', value));
  }
  function sourcesForm(state, ui) {
    var result = form('sources');
    var packages = array(state.packages);
    if (!packages.length) append(result, node('p', 'small muted', ui.preview ? 'Список приложений доступен на Android-устройстве.' : 'Доступных приложений пока нет. После установки источника откройте этот экран снова.'));
    var list = node('div', 'package-list');
    packages.forEach(function (entry) {
      var control = input('packages', 'checkbox', entry.packageName);
      control.checked = !!entry.selected;
      control.disabled = !!ui.busy || !!ui.preview;
      append(list, append(node('label', 'package-option'), control, append(node('span', 'package-text'), node('strong', '', entry.label || entry.packageName), node('small', '', entry.packageName))));
    });
    if (packages.length) append(result, list, submit('Сохранить источники', ui.busy));
    return result;
  }
  function settingsPage(state, ui) {
    var result = node('div');
    var permissions = state.permissions || {};
    var ocr = state.ocr || {};
    var access = append(node('section', 'card settings-card'), node('h2', '', 'Автоматический ввод'), node('p', 'settings-intro', 'Разрешите системный доступ и выберите нужные приложения. Новые источники сами не подключаются.'), statusLine('Доступ к уведомлениям', permissions.notificationAccess ? 'Включён' : 'Выключен', !permissions.notificationAccess), statusLine('Локальное OCR', ocr.ready ? 'Готово' : 'Не готово', !ocr.ready));
    append(access, errorNotice(permissions.notificationError, false, 'Обработка уведомлений недоступна'), action('openNotificationAccess', 'Открыть системный доступ', { className: 'button soft full', icon: 'bell', disabled: ui.busy || ui.preview }), node('p', 'form-help', 'Android предоставляет широкий доступ к уведомлениям. «Ладно» обрабатывает только выбранные ниже источники.'));
    var sources = append(node('section', 'card settings-card'), node('h2', '', 'Приложения-источники'), node('p', 'settings-intro', 'Выбирайте по названию и имени пакета. Название приложения само по себе не подтверждает, что оно принадлежит банку.'), sourcesForm(state, ui));
    var preferences = append(node('section', 'card settings-card'), node('h2', '', 'Валюта и дата'), node('p', 'settings-intro', 'Основная валюта задаёт начальный фильтр. Итоги других валют доступны отдельно.'), settingsForm(state, ui, false));
    append(result, access, sources, preferences, append(node('section', 'card settings-card'), append(node('div', 'section-heading'), node('h2', '', 'Ваши данные'), icon('lock', 19)), node('p', 'small muted', 'Учёт хранится локально на этом устройстве. Облачной синхронизации и резервного экспорта в этой версии нет. Удаление приложения или потеря телефона может привести к потере записей.')));
    return result;
  }
  function inlineCategory(ui, kind) {
    ui = ui || {};
    var result = node('div', 'inline-category');
    var toggle = action('toggleInlineCategory', 'Создать категорию здесь', { icon: 'plus', disabled: ui.busy });
    attr(toggle, 'aria-expanded', ui.inlineCategory ? 'true' : 'false');
    attr(toggle, 'aria-controls', 'inline-category-fields');
    append(result, toggle);
    var fields = node('div', 'inline-category-fields'); fields.id = 'inline-category-fields';
    fields.hidden = !ui.inlineCategory;
    var name = input('newCategoryName', 'text', ''); name.maxLength = 80; name.autocomplete = 'off';
    name.disabled = !ui.inlineCategory || !!ui.busy;
    name.placeholder = 'Например, кофе';
    append(fields, field('Название новой категории', name), attr(node('p', 'small muted', kind === 'expense' || kind === 'income' ? 'Категория для ' + (kind === 'expense' ? 'расходов' : 'доходов') + '. Текущий ввод операции сохранится.' : 'Сначала выберите вид операции: доход или расход.'), 'data-inline-category-kind', 'true'), errorNotice(ui.inlineCategoryError, true), append(node('div', 'button-row'), action('createInlineCategory', 'Создать и выбрать', { className: 'button soft', disabled: ui.busy }), action('cancelInlineCategory', 'Отмена', { className: 'button secondary', disabled: ui.busy })));
    append(result, fields);
    return result;
  }
  function transactionFields(state, record, kind, review, ui) {
    var fragment = document.createDocumentFragment();
    var kindControl = select('kind', [{ value: 'expense', label: 'Расход' }, { value: 'income', label: 'Доход' }], record ? record.kind : kind, 'Выберите вид');
    kindControl.required = true;
    attr(kindControl, 'data-transaction-kind', 'true');
    var amount = input('amount', 'text', record ? record.amount : '');
    amount.required = true;
    amount.maxLength = 64;
    amount.autocomplete = 'off';
    attr(amount, 'inputmode', 'decimal');
    amount.placeholder = '0';
    var currency = currencySelect(state, 'currency', record ? record.currency : state.selection && state.selection.currency, 'Выберите валюту');
    currency.required = true;
    attr(currency, 'data-transaction-currency', 'true');
    var date = input('date', 'date', record ? record.date : '');
    date.required = true;
    date.min = '0001-01-01';
    date.max = '9999-12-31';
    var selectedKind = record ? record.kind : kind;
    var categories = select('categoryId', array(state.categories).map(function (category) { return { value: category.id, label: category.name + (category.kind === 'income' ? ' · доход' : ' · расход') }; }), record ? record.categoryId : '', 'Без категории');
    attr(categories, 'data-category-select', 'true');
    var options = categories.options;
    for (var i = 1; i < options.length; i += 1) {
      var category = findById(array(state.categories), options[i].value);
      attr(options[i], 'data-kind', category.kind);
      if (selectedKind && category.kind !== selectedKind) { options[i].disabled = true; options[i].hidden = true; }
    }
    var memo = node('textarea');
    memo.name = 'note';
    memo.maxLength = 500;
    memo.rows = 3;
    memo.value = record && record.note ? record.note : '';
    memo.placeholder = 'Например, продукты на неделю';
    var info = currencyInfo(state, record ? record.currency : state.selection && state.selection.currency);
    var precision = info ? 'До ' + info.scale + ' знаков после разделителя для ' + info.code + '. ' : '';
    append(fragment, field('Вид операции', kindControl), field('Сумма', amount, precision + 'Точка или запятая, без пробелов и знака. Лишние дробные знаки не округляются.', 'amount-field'), append(node('div', 'field-pair'), field('Валюта', currency), field('Дата операции', date, review ? 'Проверьте дату источника.' : 'Выберите дату без сдвига по UTC.')), field('Категория · необязательно', categories, 'Выберите категорию подходящего вида или оставьте без категории.'), inlineCategory(ui, selectedKind), field('Заметка · необязательно', memo));
    return fragment;
  }
  function editor(title, subtitle, ui, formElement, id) {
    var overlay = node('div', 'editor-overlay');
    var panel = node('section', 'editor');
    attr(panel, 'role', 'dialog');
    attr(panel, 'aria-modal', 'true');
    attr(panel, 'aria-labelledby', id);
    panel.tabIndex = -1;
    append(panel, node('div', 'editor-grip'), append(node('div', 'editor-heading'), attr(node('h2', '', title), 'id', id), action('closeEditor', '', { className: 'button icon-only', icon: 'close', ariaLabel: 'Закрыть форму', disabled: ui.busy })), node('p', 'editor-subtitle', subtitle), errorNotice(ui.error, true), formElement);
    overlay.appendChild(panel);
    return overlay;
  }
  function transactionEditor(state, ui) {
    var record = ui.editTransaction ? findById(array(state.records), ui.editTransaction) : null;
    if (ui.editTransaction && !record) return editor('Операция недоступна', 'Возможно, она была удалена или вышла из выбранного периода. Закройте форму и проверьте список.', ui, node('div'), 'transaction-title');
    var kind = record ? record.kind : ui.newTransactionKind;
    var result = form('transaction', record);
    append(result, identityFields(record), transactionFields(state, record, kind, false, ui), append(node('div', 'form-actions'), action('closeEditor', 'Отмена', { className: 'button secondary', disabled: ui.busy }), submit(record ? 'Сохранить' : 'Добавить', ui.busy)));
    return editor(record ? 'Изменить операцию' : kind === 'income' ? 'Новый доход' : 'Новый расход', record ? 'Изменения сохранятся в этой записи. Источник останется прежним.' : 'Одна запись — одна операция. Категорию можно добавить позже.', ui, result, 'transaction-title');
  }
  function categoryEditor(state, ui) {
    var category = ui.editCategory ? findById(array(state.categories), ui.editCategory) : null;
    if (ui.editCategory && !category) return editor('Категория недоступна', 'Закройте форму и проверьте свежий список категорий.', ui, node('div'), 'category-title');
    var result = form('category', category);
    var name = input('name', 'text', category ? category.name : '');
    name.maxLength = 80;
    name.required = true;
    name.autocomplete = 'off';
    name.placeholder = 'Например, продукты';
    var kind = select('kind', [{ value: 'expense', label: 'Для расходов' }, { value: 'income', label: 'Для доходов' }], category ? category.kind : 'expense');
    kind.required = true;
    append(result, identityFields(category), field('Название', name), field('Вид категории', kind, 'Вид используемой категории изменить нельзя. Переименование не меняет суммы операций.'), append(node('div', 'form-actions'), action('closeEditor', 'Отмена', { className: 'button secondary', disabled: ui.busy }), submit(category ? 'Сохранить' : 'Создать', ui.busy)));
    return editor(category ? 'Изменить категорию' : 'Новая категория', 'Свой порядок для повседневных денег.', ui, result, 'category-title');
  }
  function linkOptions(state, candidate) {
    var values = [];
    array(candidate.possibleMatchIds).forEach(function (id) {
      var record = findById(array(state.records), id);
      values.push({ value: id, label: record ? 'Возможное совпадение · ' + dateLabel(record.date) + ' · ' + formattedAmount(record.amount) + ' ' + record.currency + (record.note ? ' · ' + record.note : '') : 'Возможное совпадение вне фильтра · ' + id });
    });
    array(state.records).forEach(function (record) {
      if (array(candidate.possibleMatchIds).indexOf(record.id) === -1) values.push({ value: record.id, label: dateLabel(record.date) + ' · ' + kindName(record.kind) + ' · ' + formattedAmount(record.amount) + ' ' + record.currency + (record.note ? ' · ' + record.note : '') });
    });
    return values;
  }
  function reviewEditor(state, ui) {
    var candidate = findById(array(state.imports), ui.reviewCandidate);
    if (!candidate) return editor('Материал недоступен', 'Он уже мог быть разобран. Закройте форму и проверьте свежий список.', ui, node('div'), 'review-title');
    var result = form('review', candidate);
    var decisionValue = ui.reviewDecision === 'link' || ui.reviewDecision === 'ignore' ? ui.reviewDecision : 'post';
    var decision = select('decision', [{ value: 'post', label: 'Добавить новую операцию' }, { value: 'link', label: 'Связать с существующей' }, { value: 'ignore', label: 'Не учитывать' }], decisionValue);
    attr(decision, 'data-review-decision', 'true');
    decision.required = true;
    var context = append(node('div', 'review-context'), node('strong', '', candidate.sourceLabel || sourceName(candidate.source)), reasonList(candidate));
    var post = node('fieldset', 'form-stack');
    attr(post, 'data-review-section', 'post');
    post.hidden = decisionValue !== 'post';
    post.disabled = decisionValue !== 'post';
    append(post, node('legend', 'sr-only', 'Поля новой операции'), transactionFields(state, candidate, candidate.kind, true, ui));
    if (array(candidate.possibleMatchIds).length) {
      var distinct = check('confirmDistinct', 'true', false, 'Проверено: это отдельная операция, её ещё нет в учёте.', 'Если это та же покупка, выберите «Связать с существующей», чтобы не добавить сумму повторно.', 'distinct-check');
      distinct.querySelector('input').required = true;
      post.appendChild(distinct);
    }
    var link = node('fieldset', 'form-stack');
    attr(link, 'data-review-section', 'link');
    link.hidden = decisionValue !== 'link';
    link.disabled = decisionValue !== 'link';
    var transactions = select('transactionId', linkOptions(state, candidate), '', 'Выберите операцию');
    transactions.required = true;
    append(link, node('legend', 'sr-only', 'Связь с существующей операцией'), field('Операция в учёте', transactions, 'Возможные совпадения перечислены первыми. Связь не изменит поля и не добавит сумму.'), node('p', 'decision-help', 'Совпадение суммы и времени не доказывает, что это одна покупка. Для записей вне текущего фильтра показывается их внутренний ID.'));
    var ignore = node('p', 'notice');
    attr(ignore, 'data-review-section', 'ignore');
    ignore.hidden = decisionValue !== 'ignore';
    ignore.textContent = 'Источник будет помечен как проигнорированный. Операция и сумма в учёте не появятся.';
    append(result, identityFields(candidate), context, field('Что сделать с источником', decision), post, link, ignore, append(node('div', 'form-actions'), action('closeEditor', 'Отмена', { className: 'button secondary', disabled: ui.busy }), submit('Подтвердить', ui.busy)));
    return editor('Проверить и решить', 'Проверьте поля. Разбор не влияет на итоги до вашего подтверждения.', ui, result, 'review-title');
  }
  function navigation(state, ui) {
    var result = node('nav', 'bottom-nav');
    attr(result, 'aria-label', 'Разделы приложения');
    tabs.forEach(function (tab) {
      var button = action('changeTab', tab.label, { className: 'nav-button', tab: tab.id, icon: tab.id });
      if (ui.tab === tab.id) attr(button, 'aria-current', 'page');
      var count = array(state.imports).filter(function (item) { return item.status === 'needs_review'; }).length;
      if (tab.id === 'imports' && count) append(button, attr(node('span', 'nav-count', count), 'aria-label', 'Ожидают разбора: ' + count));
      result.appendChild(button);
    });
    return result;
  }
  function render(state, ui) {
    var host = document.getElementById('app');
    if (!host) return;
    ui = ui || {};
    clear(host);
    attr(host, 'aria-busy', ui.busy ? 'true' : 'false');
    if (!state) {
      var loading = node('main', 'loading-screen');
      append(loading, node('div', 'brand-mark', 'л'), node('h1', '', 'Ладно'), node('p', '', ui.error ? 'Не удалось открыть локальный учёт.' : 'Открываем ваш учёт…'), errorNotice(ui.error, false));
      host.appendChild(loading);
      return;
    }
    if (!state.settings || !state.settings.initialized) {
      host.appendChild(setup(state, ui));
    } else {
      var tab = ui.tab || 'records';
      var shell = node('div', 'app-shell');
      var main = node('main');
      main.id = 'main-content';
      var descriptions = { records: ['Ваш месяц', 'Каждая сумма на своём месте.', '01'], charts: ['Графики', 'На что уходят деньги за выбранный месяц.', '02'], categories: ['Категории', 'Немного порядка в повседневных деньгах.', '03'], imports: ['Разбор', 'Чеки и уведомления — под вашим контролем.', '04'], settings: ['Настройки', 'Ваш учёт, ваши правила.', '05'] };
      var description = descriptions[tab] || descriptions.records;
      var hasEditor = ui.editTransaction || ui.newTransactionKind || ui.editCategory || ui.newCategory || ui.reviewCandidate;
      append(shell, topbar(), previewNotice(ui));
      append(main, heading(description[0], description[1], description[2]), hasEditor ? null : errorNotice(ui.error, false));
      if (tab === 'charts') main.appendChild(chartsPage(state, ui));
      else if (tab === 'categories') main.appendChild(categoriesPage(state, ui));
      else if (tab === 'imports') main.appendChild(importsPage(state, ui));
      else if (tab === 'settings') main.appendChild(settingsPage(state, ui));
      else main.appendChild(recordsPage(state, ui));
      append(shell, main);
      append(host, shell, navigation(state, { tab: tab }));
      if (ui.editTransaction || ui.newTransactionKind) host.appendChild(transactionEditor(state, ui));
      else if (ui.editCategory || ui.newCategory) host.appendChild(categoryEditor(state, ui));
      else if (ui.reviewCandidate) host.appendChild(reviewEditor(state, ui));
    }
    if (ui.busy) host.appendChild(attr(node('div', 'busy-line'), 'aria-hidden', 'true'));
  }
  function toast(message, kind) {
    var region = document.getElementById('toasts');
    if (!region) return;
    clear(region);
    var item = node('div', 'toast' + (kind === 'error' ? ' error' : ''), message);
    region.appendChild(item);
    root.setTimeout(function () { if (item.parentNode === region) region.removeChild(item); }, 5500);
  }

  root.AppView = { render: render, toast: toast, chartModel: chartModel };
}(window));
