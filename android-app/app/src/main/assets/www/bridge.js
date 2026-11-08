(function (root) {
  'use strict';
  var shapes = {
    state: ['month', 'currency'], settings: ['primaryCurrency', 'zoneId', 'datePolicy'],
    saveTransaction: ['id', 'expectedRevision', 'kind', 'date', 'amount', 'currency', 'categoryId', 'note'],
    deleteTransaction: ['id', 'expectedRevision'], saveCategory: ['id', 'expectedRevision', 'name', 'kind'],
    deleteCategory: ['id', 'expectedRevision'], saveSources: ['packages'],
    reviewCandidate: ['id', 'expectedRevision', 'decision', 'transaction', 'transactionId', 'confirmDistinct'],
    scanCamera: [], scanGallery: [], openNotificationAccess: []
  };
  function failure(code, message, field) {
    var error = new Error(message);
    error.code = code; error.field = field || null;
    return error;
  }
  function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
  function keys(value, allowed) {
    if (!object(value) || Object.keys(value).some(function (key) { return allowed.indexOf(key) === -1 || value[key] === undefined; })) {
      throw failure('VALIDATION', 'Недопустимые поля запроса.');
    }
  }
  function validate(action, payload) {
    if (!Object.prototype.hasOwnProperty.call(shapes, action)) throw failure('UNKNOWN_ACTION', 'Неизвестное действие.');
    keys(payload, shapes[action]);
    var allowed = shapes[action];
    if (action === 'reviewCandidate') {
      allowed = ['id', 'expectedRevision', 'decision'];
      if (payload.decision === 'post') {
        allowed = allowed.concat(['transaction', 'confirmDistinct']);
        keys(payload.transaction, ['kind', 'date', 'amount', 'currency', 'categoryId', 'note']);
        ['kind', 'date', 'amount', 'currency', 'categoryId', 'note'].forEach(function (field) {
          if (!Object.prototype.hasOwnProperty.call(payload.transaction, field)) throw failure('VALIDATION', 'Неполные поля операции.', field);
        });
        if (typeof payload.transaction.amount !== 'string' || typeof payload.confirmDistinct !== 'boolean') throw failure('VALIDATION', 'Неверный тип поля.');
      } else if (payload.decision === 'link') allowed.push('transactionId');
      else if (payload.decision !== 'ignore') throw failure('VALIDATION', 'Выберите решение.', 'decision');
      keys(payload, allowed);
    }
    if (action !== 'state') allowed.forEach(function (field) {
      if (!Object.prototype.hasOwnProperty.call(payload, field)) throw failure('VALIDATION', 'Неполные поля запроса.', field);
    });
    if (Object.prototype.hasOwnProperty.call(payload, 'expectedRevision') && payload.expectedRevision !== null &&
        (!Number.isSafeInteger(payload.expectedRevision) || payload.expectedRevision < 1)) throw failure('VALIDATION', 'Некорректная версия записи.', 'expectedRevision');
    if (Object.prototype.hasOwnProperty.call(payload, 'amount') && typeof payload.amount !== 'string') throw failure('VALIDATION', 'Сумма должна быть строкой.', 'amount');
  }
  var preview = typeof root.AndroidApp === 'undefined' || root.AndroidApp === null;
  function request(action, payload) {
    return Promise.resolve().then(function () {
      payload = payload === undefined ? {} : payload;
      validate(action, payload);
      var json;
      try { json = JSON.stringify(payload); } catch (ignore) { throw failure('BAD_JSON', 'Не удалось подготовить запрос.'); }
      // A conservative UTF-8 cap. Native repeats the authoritative byte/schema validation.
      if (new root.TextEncoder().encode(json).length > 65536) throw failure('LIMIT_EXCEEDED', 'Запрос превышает допустимый размер.');
      if (preview) {
        if (!root.PreviewAdapter || typeof root.PreviewAdapter.request !== 'function') throw failure('UNAVAILABLE', 'Предпросмотр недоступен.');
        return root.PreviewAdapter.request(action, JSON.parse(json));
      }
      if (!root.AndroidApp || typeof root.AndroidApp.request !== 'function') throw failure('UNAVAILABLE', 'Нативный мост недоступен.');
      var raw;
      try { raw = root.AndroidApp.request(action, json); } catch (ignore) { throw failure('INTERNAL', 'Не удалось обратиться к локальному хранилищу.'); }
      var envelope;
      try { envelope = JSON.parse(raw); } catch (ignore) { throw failure('BAD_JSON', 'Получен некорректный ответ приложения.'); }
      if (!object(envelope) || typeof envelope.ok !== 'boolean') throw failure('BAD_JSON', 'Получен некорректный ответ приложения.');
      if (!envelope.ok) {
        if (!object(envelope.error) || typeof envelope.error.code !== 'string' || typeof envelope.error.message !== 'string') throw failure('BAD_JSON', 'Получена некорректная ошибка приложения.');
        throw failure(envelope.error.code, envelope.error.message, typeof envelope.error.field === 'string' ? envelope.error.field : null);
      }
      if (!object(envelope.data) || envelope.error !== null) throw failure('BAD_JSON', 'Получен некорректный ответ приложения.');
      return envelope.data;
    });
  }
  root.NativeBridge = { request: request, preview: preview };
}(window));
