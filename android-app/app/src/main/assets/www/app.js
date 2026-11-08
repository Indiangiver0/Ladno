(function (root) {
  'use strict';
  var document = root.document;
  var state = null, pendingState = null;
  var filter = { month: null, currency: null };
  var ui = {
    tab: 'records', editTransaction: null, newTransactionKind: null,
    editCategory: null, newCategory: false, reviewCandidate: null, reviewDecision: 'post',
    inlineCategory: false, inlineCategoryError: null,
    busy: false, error: null, preview: !!root.NativeBridge.preview
  };
  var drafts = Object.create(null), dirty = Object.create(null);
  var querySerial = 0, pollTimer = null, jobStatuses = Object.create(null), notificationErrorKey = '';
  var opener = null, confirmation = null, lastReadError = null;

  function array(value) { return Array.isArray(value) ? value : []; }
  function list(selector, parent) { return Array.prototype.slice.call((parent || document).querySelectorAll(selector)); }
  function editorOpen() { return !!(ui.editTransaction || ui.newTransactionKind || ui.editCategory || ui.newCategory || ui.reviewCandidate); }
  function formKey(form) {
    var existing = form.getAttribute('data-draft-key');
    if (existing) return existing;
    var key = form.getAttribute('data-form') + ':' + (form.getAttribute('data-id') || '') +
      (form.getAttribute('data-form') === 'transaction' && !form.getAttribute('data-id') ? ':' + ui.newTransactionKind : '');
    form.setAttribute('data-draft-key', key);
    return key;
  }
  function controls(form) { return list('input[name],select[name],textarea[name]', form); }
  function forms() { return list('form[data-form]', document.getElementById('app')); }
  function visibleDirty() { return forms().some(function (form) { return !!dirty[formKey(form)]; }); }
  function controlIndex(control, form) { return controls(form).filter(function (entry) { return entry.name === control.name; }).indexOf(control); }
  function focusDescription(element) {
    if (!element || element === document.body) return null;
    var form = element.closest && element.closest('form[data-form]');
    if (form && element.name) {
      var result = { form: formKey(form), name: element.name, index: controlIndex(element, form) };
      try { result.start = element.selectionStart; result.end = element.selectionEnd; result.direction = element.selectionDirection; } catch (ignore) { /* Not a text input. */ }
      return result;
    }
    var button = element.closest && element.closest('[data-action]');
    if (button) return { action: button.getAttribute('data-action'), id: button.getAttribute('data-id'), tab: button.getAttribute('data-tab'), name: button.name || '' };
    return null;
  }
  function focusTarget(description) {
    if (!description) return null;
    if (description.form) {
      var form = forms().find(function (entry) { return formKey(entry) === description.form; });
      return form ? controls(form).filter(function (entry) { return entry.name === description.name; })[description.index] : null;
    }
    return list('[data-action]').find(function (entry) {
      return entry.getAttribute('data-action') === description.action && entry.getAttribute('data-id') === description.id &&
        entry.getAttribute('data-tab') === description.tab && (entry.name || '') === description.name;
    }) || null;
  }
  function focus(element, description) {
    if (!element || element.disabled || element.closest('[hidden]')) return false;
    try { element.focus({ preventScroll: true }); } catch (ignore) { element.focus(); }
    if (description && description.start !== null && description.start !== undefined && element.setSelectionRange) {
      try { element.setSelectionRange(description.start, description.end, description.direction || 'none'); } catch (ignore) { /* Date/select control. */ }
    }
    return document.activeElement === element;
  }
  function remember(skip) {
    forms().forEach(function (form) {
      var key = formKey(form);
      if (key === skip) return;
      if (!dirty[key] && !form.closest('[role="dialog"]')) return;
      drafts[key] = controls(form).map(function (control) {
        return { name: control.name, index: controlIndex(control, form), value: control.value, checked: control.checked };
      });
    });
  }
  function restore() {
    forms().forEach(function (form) {
      var saved = drafts[formKey(form)];
      if (!saved) return;
      saved.forEach(function (entry) {
        var control = controls(form).filter(function (item) { return item.name === entry.name; })[entry.index];
        if (!control) return;
        control.value = entry.value;
        if (control.type === 'checkbox' || control.type === 'radio') control.checked = entry.checked;
      });
    });
  }
  function syncKind(form) {
    var kind = form.querySelector('[data-transaction-kind]'), categories = form.querySelector('[data-category-select]');
    if (!kind || !categories) return;
    var inlineHelp = form.querySelector('[data-inline-category-kind]');
    if (inlineHelp) inlineHelp.textContent = kind.value === 'expense' || kind.value === 'income' ? 'Категория для ' + (kind.value === 'expense' ? 'расходов' : 'доходов') + '. Текущий ввод операции сохранится.' : 'Сначала выберите вид операции: доход или расход.';
    list('option[data-kind]', categories).forEach(function (option) {
      option.disabled = !!kind.value && option.getAttribute('data-kind') !== kind.value;
      option.hidden = option.disabled;
      if (option.disabled && option.selected) categories.value = '';
    });
  }
  function syncReview(form) {
    var decision = form.querySelector('[data-review-decision]');
    if (!decision) return;
    ui.reviewDecision = decision.value;
    list('[data-review-section]', form).forEach(function (section) {
      section.hidden = section.getAttribute('data-review-section') !== decision.value;
      if (section.tagName === 'FIELDSET') section.disabled = section.hidden;
    });
  }
  function syncCurrency(form) {
    var control = form.querySelector('[data-transaction-currency]'), help = form.querySelector('.amount-field small');
    if (!control || !help || !state) return;
    var currency = array(state.currencies).find(function (entry) { return entry.code === control.value; });
    help.textContent = (currency ? 'До ' + currency.scale + ' знаков после разделителя для ' + currency.code + '. ' : '') + 'Точка или запятая, без пробелов и знака. Лишние дробные знаки не округляются.';
  }
  function modal() { var panels = list('[role="dialog"]'); return panels.length ? panels[panels.length - 1] : null; }
  function focusable(panel) {
    return list('button,input,select,textarea,a[href],[tabindex]', panel).filter(function (entry) {
      return entry.tabIndex >= 0 && !entry.matches(':disabled') && entry.type !== 'hidden' && !entry.closest('[hidden]');
    });
  }
  function constrainModal() {
    var panel = modal(), host = document.getElementById('app');
    if (!panel || !host) return;
    Array.prototype.forEach.call(host.children, function (element) {
      if (!element.contains(panel)) { element.setAttribute('aria-hidden', 'true'); element.inert = true; }
    });
  }
  function renderConfirmation() {
    if (!confirmation) return;
    var overlay = document.createElement('div'); overlay.className = 'editor-overlay';
    var panel = document.createElement('section'); panel.className = 'editor'; panel.tabIndex = -1;
    panel.setAttribute('role', 'dialog'); panel.setAttribute('aria-modal', 'true'); panel.setAttribute('aria-labelledby', 'delete-title'); panel.setAttribute('aria-describedby', 'delete-explanation');
    var dismiss = confirmation.action === 'dismissImport';
    var heading = document.createElement('h2'); heading.id = 'delete-title'; heading.textContent = dismiss ? 'Убрать неучтённый чек?' : confirmation.action === 'deleteCategory' ? 'Удалить категорию?' : 'Удалить операцию?';
    var detail = document.createElement('p'); detail.id = 'delete-explanation'; detail.className = 'editor-subtitle';
    detail.textContent = dismiss ? 'Скан исчезнет из раздела «Разбор». Расход не будет создан, суммы учёта не изменятся.' : confirmation.action === 'deleteCategory' ? 'Операции останутся в учёте без категории. Их суммы и даты сохранятся.' : 'Операция перестанет входить в итоги. Связь с исходным чеком или уведомлением сохранится, чтобы повтор не создал её заново.';
    var actions = document.createElement('div'); actions.className = 'form-actions';
    [['cancelDelete', 'Отмена', 'button secondary'], ['confirmDelete', dismiss ? 'Убрать' : 'Удалить', 'button primary']].forEach(function (entry) {
      var button = document.createElement('button'); button.type = 'button'; button.className = entry[2]; button.textContent = entry[1]; button.setAttribute('data-action', entry[0]); button.disabled = ui.busy; actions.appendChild(button);
    });
    if (ui.error) { var error = document.createElement('p'); error.className = 'form-inline-error'; error.setAttribute('role', 'alert'); error.textContent = ui.error.message; panel.appendChild(error); }
    panel.appendChild(heading); panel.appendChild(detail); panel.appendChild(actions); overlay.appendChild(panel);
    document.getElementById('app').appendChild(overlay);
  }
  function markError() {
    if (ui.inlineCategoryError) {
      var categoryInput = (modal() || document.getElementById('app')).querySelector(ui.inlineCategoryError.field === 'kind' ? '[name="kind"]' : 'input[name="newCategoryName"]');
      if (categoryInput && !categoryInput.disabled) {
        categoryInput.setAttribute('aria-invalid', 'true');
        var categoryNotice = categoryInput.closest('form');
        categoryNotice = categoryNotice && categoryNotice.querySelector('.inline-category');
        categoryNotice = categoryNotice && categoryNotice.querySelector('.form-inline-error');
        if (categoryNotice) { categoryNotice.id = 'inline-category-error'; categoryInput.setAttribute('aria-describedby', categoryNotice.id); }
      }
    }
    if (!ui.error || !ui.error.field) return;
    var scope = modal() || document.getElementById('app'), field = ui.error.field.replace(/^transaction\./, '');
    var control = list('input[name],select[name],textarea[name]', scope).find(function (entry) { return entry.name === field && !entry.matches(':disabled'); });
    var notice = scope.querySelector('.form-inline-error,.notice.error');
    if (!control) return;
    control.setAttribute('aria-invalid', 'true');
    if (notice) { notice.id = 'active-form-error'; control.setAttribute('aria-describedby', 'active-form-error'); }
  }
  function paint(options) {
    options = options || {};
    var previous = options.focus || focusDescription(document.activeElement);
    if (options.remember !== false) remember(options.skipForm);
    root.AppView.render(state, ui);
    restore();
    forms().forEach(function (form) { syncReview(form); syncKind(form); syncCurrency(form); });
    renderConfirmation(); constrainModal(); markError();
    var target = focusTarget(previous), panel = modal();
    if (target && (!panel || panel.contains(target)) && focus(target, previous)) return;
    if (panel) {
      var initial = confirmation ? panel.querySelector('[data-action="cancelDelete"]') : panel.querySelector('input:not([type="hidden"]),select,textarea');
      if (!focus(initial)) focus(panel);
    } else if (options.restoreOpener && !focus(focusTarget(opener))) {
      var heading = document.querySelector('main h1'); if (heading) { heading.tabIndex = -1; focus(heading); }
    }
  }
  function normalizedError(error) {
    return { code: error && typeof error.code === 'string' ? error.code : 'INTERNAL', message: error && typeof error.message === 'string' ? error.message : 'Не удалось выполнить действие. Попробуйте снова.', field: error && typeof error.field === 'string' ? error.field : null };
  }
  function inspectNative(next) {
    array(next.jobs).forEach(function (job) {
      var previous = jobStatuses[job.jobId]; jobStatuses[job.jobId] = job.status;
      if (previous === job.status) return;
      if (job.status === 'failed') root.AppView.toast(job.error && job.error.message || 'Обработка не выполнена. Проверьте раздел «Разбор».', 'error');
      else if (job.status === 'cancelled') root.AppView.toast('Действие отменено. Новая операция не создана.');
      else if (job.status === 'completed') {
        var outcome = job.outcome || {};
        var messages = {
          posted: 'Расход из чека добавлен. Он доступен в операциях.',
          needs_review: 'Проверьте распознанные поля чека в разделе «Разбор».',
          linked: 'Чек связан с учтённой операцией. Второй расход не добавлен.',
          duplicate: 'Этот скан уже обрабатывался. Второй расход не добавлен.',
          ignored: 'Этот чек ранее убран из разбора. Расход не добавлен.'
        };
        root.AppView.toast(job.type === 'openNotificationAccess' ? 'Системные настройки закрыты. Фактический доступ показан в настройках.' : messages[outcome.status] || 'Обработка завершена. Проверьте операции и раздел «Разбор».');
      }
    });
    var nativeError = next.permissions && next.permissions.notificationError;
    var key = nativeError ? nativeError.code + ':' + nativeError.message : '';
    if (key && key !== notificationErrorKey) root.AppView.toast(nativeError.message || 'Автоматический приём уведомлений недоступен.', 'error');
    notificationErrorKey = key;
    if (pollTimer !== null) { root.clearTimeout(pollTimer); pollTimer = null; }
    if (array(next.jobs).some(function (job) { return job.status === 'queued' || job.status === 'running'; })) {
      pollTimer = root.setTimeout(function () { pollTimer = null; refresh(); }, 1400);
    }
  }
  function refresh(options) {
    options = options || {};
    var serial = ++querySerial;
    return root.NativeBridge.request('state', { month: filter.month, currency: filter.currency }).then(function (next) {
      if (serial !== querySerial) return false;
      if (ui.error === lastReadError) ui.error = null;
      lastReadError = null;
      inspectNative(next);
      if (options.force || !state || (!editorOpen() && !confirmation && !visibleDirty())) { state = next; pendingState = null; }
      else pendingState = next;
      if (options.paint !== false && pendingState === null) paint({ skipForm: options.skipForm });
      return true;
    }).catch(function (error) {
      if (serial !== querySerial) return false;
      if (!ui.error || ui.error === lastReadError) { ui.error = normalizedError(error); lastReadError = ui.error; }
      if (options.paint !== false) paint();
      return false;
    });
  }
  function usePending() { if (pendingState) { state = pendingState; pendingState = null; } }
  function clearEditor() {
    ui.editTransaction = null; ui.newTransactionKind = null; ui.editCategory = null; ui.newCategory = false; ui.reviewCandidate = null; ui.reviewDecision = 'post';
    ui.inlineCategory = false; ui.inlineCategoryError = null;
  }
  function closeEditor() {
    if (ui.busy) return;
    var active = document.querySelector('[role="dialog"] form[data-form]');
    if (active) { delete drafts[formKey(active)]; delete dirty[formKey(active)]; }
    clearEditor(); ui.error = null; usePending();
    paint({ remember: false, restoreOpener: true, focus: opener });
  }
  function find(items, id) { return array(items).find(function (entry) { return entry.id === id; }); }
  function openEditor(action, id, element) {
    remember(); usePending(); clearEditor(); confirmation = null; ui.error = null; opener = focusDescription(element);
    if (action === 'newExpense') ui.newTransactionKind = 'expense';
    else if (action === 'newIncome') ui.newTransactionKind = 'income';
    else if (action === 'newCategory') ui.newCategory = true;
    else if (action === 'editTransaction') ui.editTransaction = id;
    else if (action === 'editCategory') ui.editCategory = id;
    else if (action === 'reviewCandidate') ui.reviewCandidate = id;
    paint({ remember: false });
  }
  function failure(code, message, field) { var error = new Error(message); error.code = code; error.field = field || null; throw error; }
  function value(form, name) { var control = form.elements.namedItem(name); return control ? control.value : ''; }
  function checked(form, name) { var control = form.elements.namedItem(name); return !!(control && control.checked); }
  function transactionEditor(element) {
    var form = element && element.closest('form[data-form]');
    return form && (form.getAttribute('data-form') === 'transaction' || form.getAttribute('data-form') === 'review') ? form : null;
  }
  function setDraftValue(key, name, next) {
    var saved = drafts[key] || (drafts[key] = []);
    var entry = saved.find(function (item) { return item.name === name && item.index === 0; });
    if (entry) entry.value = next;
    else saved.push({ name: name, index: 0, value: next, checked: false });
  }
  function toggleInlineCategory(element, show) {
    var form = transactionEditor(element); if (!form) return;
    remember(); ui.inlineCategory = show === undefined ? !ui.inlineCategory : show; ui.inlineCategoryError = null;
    paint({ remember: false });
    var next = (modal() || document).querySelector(ui.inlineCategory ? 'input[name="newCategoryName"]' : '[data-action="toggleInlineCategory"]');
    focus(next);
  }
  function createInlineCategory(element) {
    var form = transactionEditor(element); if (!form || ui.busy) return;
    var key = formKey(form), kind = value(form, 'kind'), name = value(form, 'newCategoryName').trim();
    remember(); dirty[key] = true; ui.inlineCategoryError = null;
    if (kind !== 'expense' && kind !== 'income') {
      ui.inlineCategoryError = { code: 'VALIDATION', message: 'Сначала выберите вид операции: доход или расход.', field: 'kind' };
      paint({ remember: false }); focus((modal() || document).querySelector('[name="kind"]')); return;
    }
    if (!name || Array.from(name).length > 80) {
      ui.inlineCategoryError = { code: 'VALIDATION', message: 'Введите название категории от 1 до 80 символов.', field: 'newCategoryName' };
      paint({ remember: false }); focus((modal() || document).querySelector('[name="newCategoryName"]')); return;
    }
    // This is an independent category mutation: unfinished amount/date fields are not submitted.
    ui.busy = true; paint({ remember: false });
    root.NativeBridge.request('saveCategory', { id: null, expectedRevision: null, name: name, kind: kind }).then(function (result) {
      var category = result.category;
      if (!category || typeof category.id !== 'string' || category.kind !== kind) failure('BAD_JSON', 'Не удалось прочитать созданную категорию. Обновите список категорий.');
      // Keep the committed category usable even if the following state read fails.
      state.categories = array(state.categories).filter(function (item) { return item.id !== category.id; }).concat([category]);
      setDraftValue(key, 'categoryId', category.id); setDraftValue(key, 'newCategoryName', '');
      ui.inlineCategory = false;
      return refresh({ force: true, paint: false }).then(function () { root.AppView.toast('Категория создана и выбрана. Ввод операции сохранён.'); });
    }).catch(function (error) {
      ui.inlineCategoryError = normalizedError(error);
      if (ui.inlineCategoryError.field === 'name') ui.inlineCategoryError.field = 'newCategoryName';
    }).then(function () {
      ui.busy = false; paint({ remember: false });
      focus((modal() || document).querySelector(ui.inlineCategoryError ? '[name="newCategoryName"]' : '[name="categoryId"]'));
    });
  }
  function identity(form) {
    var id = value(form, 'id'), raw = value(form, 'expectedRevision');
    if (!id && !raw) return { id: null, expectedRevision: null };
    if (!id || !/^[1-9][0-9]*$/.test(raw) || !Number.isSafeInteger(Number(raw))) failure('VALIDATION', 'Некорректная версия записи. Закройте форму и откройте её снова.', 'expectedRevision');
    return { id: id, expectedRevision: Number(raw) };
  }
  function transactionFields(form) {
    var payload = { kind: value(form, 'kind'), date: value(form, 'date'), amount: value(form, 'amount'), currency: value(form, 'currency'), categoryId: value(form, 'categoryId') || null, note: value(form, 'note') };
    var currency = array(state.currencies).find(function (entry) { return entry.code === payload.currency; });
    if (!currency) failure('CURRENCY_UNKNOWN', 'Выберите валюту операции.', 'currency');
    if (!/^(?:0|[1-9][0-9]{0,23})(?:[.,][0-9]+)?$/.test(payload.amount) || payload.amount.length > 64 || !/[1-9]/.test(payload.amount)) failure('MONEY_INVALID', 'Введите положительную сумму без знака, пробелов и ведущих нулей. Например, 12,50.', 'amount');
    var fraction = payload.amount.replace(',', '.').split('.')[1] || '';
    if (fraction.length > currency.scale) failure('MONEY_SCALE', 'Для ' + currency.code + ' допустимо знаков после разделителя: ' + currency.scale + '. Лишние знаки не округляются.', 'amount');
    if (payload.kind !== 'income' && payload.kind !== 'expense') failure('VALIDATION', 'Выберите доход или расход.', 'kind');
    return payload;
  }
  function submission(form) {
    var type = form.getAttribute('data-form'), payload;
    if (type === 'transaction') return { action: 'saveTransaction', payload: Object.assign(identity(form), transactionFields(form)), message: 'Операция сохранена.' };
    if (type === 'category') return { action: 'saveCategory', payload: Object.assign(identity(form), { name: value(form, 'name'), kind: value(form, 'kind') }), message: 'Категория сохранена.' };
    if (type === 'settings') {
      if (!checked(form, 'datePolicy')) failure('VALIDATION', 'Подтвердите правило даты уведомлений.', 'datePolicy');
      return { action: 'settings', payload: { primaryCurrency: value(form, 'primaryCurrency'), zoneId: value(form, 'zoneId'), datePolicy: 'POST_TIME_IN_ZONE' }, message: 'Настройки сохранены.' };
    }
    if (type === 'sources') return { action: 'saveSources', payload: { packages: controls(form).filter(function (control) { return control.name === 'packages' && control.checked && !control.disabled; }).map(function (control) { return control.value; }) }, message: 'Источники сохранены.' };
    if (type === 'review') {
      payload = Object.assign(identity(form), { decision: value(form, 'decision') });
      if (payload.decision === 'post') { payload.transaction = transactionFields(form); payload.confirmDistinct = checked(form, 'confirmDistinct'); }
      else if (payload.decision === 'link') payload.transactionId = value(form, 'transactionId');
      else if (payload.decision !== 'ignore') failure('VALIDATION', 'Выберите решение.', 'decision');
      return { action: 'reviewCandidate', payload: payload, message: payload.decision === 'post' ? 'Операция добавлена.' : payload.decision === 'link' ? 'Источник связан с операцией. Итог не увеличен.' : 'Источник отмечен как неучтённый.' };
    }
    failure('UNKNOWN_ACTION', 'Неизвестная форма.');
  }
  function mutate(action, payload, form, message) {
    if (ui.busy) return Promise.resolve();
    var key = form ? formKey(form) : null, saved = false;
    ui.busy = true; ui.error = null; paint();
    return root.NativeBridge.request(action, payload).then(function () {
      saved = true;
      if (key) { delete drafts[key]; delete dirty[key]; }
      if (action === 'settings' && (!state.settings || !state.settings.initialized)) filter.currency = null;
      if (action === 'saveTransaction' || action === 'saveCategory' || action === 'reviewCandidate') clearEditor();
      confirmation = null;
      return refresh({ force: true, paint: false });
    }).then(function () {
      if (saved) root.AppView.toast(message);
    }).catch(function (error) {
      ui.error = normalizedError(error);
      if (ui.error.code === 'CONFLICT' || ui.error.code === 'NOT_FOUND') {
        var dismiss = confirmation && confirmation.action === 'dismissImport';
        if (dismiss) confirmation = null;
        return refresh({ force: !!dismiss, paint: false });
      }
      return null;
    }).then(function () {
      ui.busy = false;
      if (saved) usePending();
      paint({ skipForm: saved ? key : null, restoreOpener: saved, focus: saved ? opener : null });
      if (ui.error && ui.error.field) {
        var scope = modal() || document.getElementById('app');
        var invalid = scope.querySelector('[aria-invalid="true"]'); if (invalid) focus(invalid);
      }
    });
  }
  function confirmDelete(action, id, element) {
    var item = find(action === 'dismissImport' ? state.imports : action === 'deleteTransaction' ? state.records : state.categories, id);
    if (!item) { ui.error = { code: 'NOT_FOUND', message: 'Запись недоступна. Обновите список.', field: null }; paint(); refresh(); return; }
    if (action === 'dismissImport' && (item.source !== 'receipt' || item.status !== 'needs_review')) return;
    opener = focusDescription(element);
    confirmation = { action: action, id: item.id, expectedRevision: item.revision }; ui.error = null; paint();
  }
  function viewScanOutcome(id, element) {
    var job = findJob(id), outcome = job && job.outcome;
    if (!outcome) return;
    opener = focusDescription(element); ui.busy = true; ui.error = null;
    if (outcome.transactionId && outcome.month && outcome.currency) filter = { month: outcome.month, currency: outcome.currency };
    paint();
    refresh({ force: true, paint: false }).then(function (read) {
      ui.busy = false;
      if (!read) { paint(); return; }
      var candidate = find(state.imports, outcome.candidateId);
      if (candidate && candidate.status === 'needs_review') {
        ui.tab = 'imports'; openEditor('reviewCandidate', candidate.id, element); return;
      }
      ui.tab = outcome.transactionId ? 'records' : 'imports'; paint();
      if (outcome.transactionId) {
        var row = list('[data-record-id]').find(function (item) { return item.getAttribute('data-record-id') === outcome.transactionId; });
        if (row) { row.tabIndex = -1; focus(row); row.scrollIntoView({ block: 'center' }); }
        else root.AppView.toast('Операция больше не доступна в учёте. Второй расход не добавлен.');
      } else root.AppView.toast('Этот скан уже разобран. Новая операция не добавлена.');
    });
  }
  function findJob(id) { return array((pendingState || state || {}).jobs).find(function (job) { return job.jobId === id; }); }
  function changeSelection() {
    var month = document.querySelector('input[name="month"][data-action="changeSelection"]');
    var currency = document.querySelector('select[name="currency"][data-action="changeSelection"]');
    if (!month || !currency || !month.checkValidity() || !currency.value) return;
    filter = { month: month.value, currency: currency.value }; ui.error = null; ui.busy = true;
    refresh({ force: true, paint: false }).then(function () { ui.busy = false; paint(); });
  }
  document.addEventListener('submit', function (event) {
    var form = event.target.closest('form[data-form]'); if (!form) return;
    event.preventDefault(); if (ui.busy) return;
    dirty[formKey(form)] = true;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    try {
      var operation = submission(form);
      mutate(operation.action, operation.payload, form, operation.message);
    } catch (error) {
      ui.error = normalizedError(error); paint();
      var invalid = (modal() || document.getElementById('app')).querySelector('[aria-invalid="true"]'); if (invalid) focus(invalid);
    }
  });
  document.addEventListener('click', function (event) {
    var element = event.target.closest && event.target.closest('button[data-action]');
    if (!element || element.disabled || ui.busy) return;
    var action = element.getAttribute('data-action'), id = element.getAttribute('data-id');
    if (modal() && !modal().contains(element)) return;
    if (['newExpense', 'newIncome', 'newCategory', 'editTransaction', 'editCategory', 'reviewCandidate'].indexOf(action) !== -1) openEditor(action, id, element);
    else if (action === 'closeEditor') closeEditor();
    else if (action === 'changeTab') {
      var tab = element.getAttribute('data-tab'); if (['records', 'charts', 'categories', 'imports', 'settings'].indexOf(tab) === -1) return;
      remember(); ui.tab = tab; ui.error = null; usePending(); paint({ remember: false }); refresh();
    } else if (action === 'toggleInlineCategory') toggleInlineCategory(element);
    else if (action === 'cancelInlineCategory') toggleInlineCategory(element, false);
    else if (action === 'createInlineCategory') createInlineCategory(element);
    else if (action === 'viewScanOutcome') viewScanOutcome(id, element);
    else if (action === 'deleteTransaction' || action === 'deleteCategory' || action === 'dismissImport') confirmDelete(action, id, element);
    else if (action === 'cancelDelete') { confirmation = null; ui.error = null; usePending(); paint({ restoreOpener: true, focus: opener }); }
    else if (action === 'confirmDelete' && confirmation) {
      var dismiss = confirmation.action === 'dismissImport';
      var payload = { id: confirmation.id, expectedRevision: confirmation.expectedRevision };
      if (dismiss) payload.decision = 'ignore';
      mutate(dismiss ? 'reviewCandidate' : confirmation.action, payload, null, dismiss ? 'Чек убран из разбора. Расход не добавлен.' : confirmation.action === 'deleteCategory' ? 'Категория удалена. Операции сохранены.' : 'Операция удалена из учёта.');
    }
    else if (action === 'scanCamera' || action === 'scanGallery' || action === 'openNotificationAccess') mutate(action, {}, null, action === 'openNotificationAccess' ? 'Запрошено открытие системных настроек.' : 'Запрошен выбор изображения. Итог появится после обработки.');
  });
  function changed(event) {
    var element = event.target, form = element.closest && element.closest('form[data-form]');
    if (!form) return;
    dirty[formKey(form)] = true;
    if (element.name === 'newCategoryName' || element.hasAttribute('data-transaction-kind')) {
      ui.inlineCategoryError = null;
      var categoryBlock = form.querySelector('.inline-category');
      if (categoryBlock) list('.form-inline-error', categoryBlock).forEach(function (notice) { notice.hidden = true; });
    }
    element.removeAttribute('aria-invalid'); element.removeAttribute('aria-describedby');
    var clientError = document.getElementById('client-validation'); if (clientError) clientError.remove();
    if (ui.error) { ui.error = null; list('.form-inline-error', form.closest('[role="dialog"]') || form).forEach(function (notice) { notice.hidden = true; }); }
    if (element.hasAttribute('data-review-decision')) syncReview(form);
    if (element.hasAttribute('data-transaction-kind')) syncKind(form);
    if (element.hasAttribute('data-transaction-currency')) syncCurrency(form);
  }
  document.addEventListener('input', changed);
  document.addEventListener('change', function (event) {
    if (event.target.getAttribute('data-action') === 'changeSelection' && !event.target.closest('form')) { if (!ui.busy) changeSelection(); return; }
    changed(event);
  });
  document.addEventListener('invalid', function (event) {
    var control = event.target, form = control.closest && control.closest('form[data-form]'); if (!form) return;
    control.setAttribute('aria-invalid', 'true');
    var notice = document.getElementById('client-validation');
    if (!notice) { notice = document.createElement('p'); notice.id = 'client-validation'; notice.className = 'form-inline-error'; notice.setAttribute('role', 'alert'); form.appendChild(notice); }
    notice.textContent = control.validationMessage || 'Заполните обязательное поле.'; control.setAttribute('aria-describedby', 'client-validation');
  }, true);
  document.addEventListener('keydown', function (event) {
    var panel = modal(); if (!panel) return;
    if (event.key === 'Escape') {
      event.preventDefault(); if (ui.busy) return;
      if (confirmation) { confirmation = null; ui.error = null; usePending(); paint({ restoreOpener: true, focus: opener }); }
      else closeEditor();
    } else if (event.key === 'Tab') {
      var entries = focusable(panel), first = entries[0], last = entries[entries.length - 1];
      if (!entries.length) { event.preventDefault(); focus(panel); return; }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === panel || !panel.contains(document.activeElement))) { event.preventDefault(); focus(last); }
      else if (!event.shiftKey && (document.activeElement === last || !panel.contains(document.activeElement))) { event.preventDefault(); focus(first); }
    }
  });
  document.addEventListener('focusin', function (event) { var panel = modal(); if (panel && !panel.contains(event.target)) focus(focusable(panel)[0] || panel); });
  document.addEventListener('visibilitychange', function () { if (!document.hidden) refresh(); });
  root.NativeEvents = { onRefresh: function () { refresh(); } };
  root.addEventListener('pageshow', function (event) { if (event.persisted) refresh(); });
  paint(); refresh();
}(window));
