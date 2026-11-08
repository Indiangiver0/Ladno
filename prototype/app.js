/* Memory-only controller. Rendering is delegated to LadnoView; money to LadnoModel. */
(function (root) {
  'use strict';
  const model = root.LadnoModel;
  const initialState = () => ({ data: model.clone(root.LadnoDemoData), section: 'overview', month: '2026-10', filter: 'all', screenState: 'ready', debtDirection: 'owe', form: null, notice: '' });
  let state = initialState();
  let opener = null;
  let openerKey = null;
  let controllerRoot = null;
  let backgroundNodes = [];

  function restoreBackground() {
    for (const entry of backgroundNodes) entry.node.inert = entry.inert;
    backgroundNodes = [];
    if (root.document) root.document.body.style.overflow = '';
  }

  function isolateDialog() {
    if (!root.document) return;
    const panel = root.document.querySelector('[role="dialog"]');
    if (!panel) return;
    // Make every sibling of the dialog and its ancestors inert, while keeping its
    // backdrop usable. Focus trapping below also works on older offline browsers.
    let child = panel;
    while (child && child !== controllerRoot && child.parentElement) {
      for (const sibling of child.parentElement.children) {
        if (sibling !== child && !sibling.classList.contains('form-backdrop') && !sibling.classList.contains('modal-backdrop')) {
          backgroundNodes.push({ node: sibling, inert: sibling.inert });
          sibling.inert = true;
        }
      }
      child = child.parentElement;
    }
    root.document.body.style.overflow = 'hidden';
  }

  function render(focusTarget) {
    if (!root.document || !root.LadnoView) return;
    restoreBackground();
    root.LadnoView.render(state);
    if (state.form) isolateDialog();
    if (focusTarget === 'form') root.document.getElementById('form-title')?.focus();
    if (focusTarget === 'invalid') root.document.querySelector('[role="dialog"] [aria-invalid="true"]')?.focus();
    if (focusTarget === 'main') root.document.getElementById('main-content')?.focus();
  }

  function readForm(formNode) {
    const values = {};
    const entries = new root.FormData(formNode);
    for (const [name, value] of entries) values[name] = typeof value === 'string' ? value : '';
    return values;
  }

  function rememberOpener(element) {
    opener = element || root.document?.activeElement || null;
    openerKey = opener && opener.dataset ? { action: opener.dataset.action, id: opener.dataset.id, value: opener.dataset.value } : null;
  }

  function restoreOpener() {
    if (!root.document) return;
    if (opener && opener.isConnected) opener.focus();
    else if (openerKey && openerKey.action) {
      const matches = Array.from(root.document.querySelectorAll('[data-action]'));
      const match = matches.find(element => element.dataset.action === openerKey.action && element.dataset.id === openerKey.id && element.dataset.value === openerKey.value);
      if (match) match.focus();
      else root.document.getElementById('main-content')?.focus();
    } else root.document.getElementById('main-content')?.focus();
    opener = null;
    openerKey = null;
  }

  function openForm(kind, id, source) {
    rememberOpener(source);
    let values;
    if (kind === 'operation') {
      state.section = 'operations';
      values = { type: 'expense', amount: '', date: '2026-10-30', accountId: state.data.accounts[0]?.id || '', categoryId: state.data.categories.find(item => item.kind === 'expense' && !item.archived)?.id || '', note: '' };
    } else if (kind === 'goal') values = { name: '', target: '', accumulated: '0' };
    else if (kind === 'goal-edit') {
      const goal = state.data.goals.find(item => item.id === id);
      if (!goal) return;
      values = { accumulated: goal.accumulated.amount.replace('.', ',') };
    } else if (kind === 'debt') values = { direction: state.debtDirection, label: '', amount: '', dueDate: '' };
    else if (kind === 'repayment') {
      if (!state.data.debts.some(item => item.id === id)) return;
      values = { amount: '', date: '2026-10-30' };
    } else return;
    state.form = { kind, id: id || null, values, errors: {} };
    state.notice = '';
    render('form');
  }

  function submit(values) {
    if (!state.form) return { ok: false, errors: { form: 'Форма закрыта' } };
    // Preserve exactly what the user typed, even when validation fails.
    state.form.values = { ...state.form.values, ...values };
    const kind = state.form.kind;
    const result = model.applyForm(state.data, kind, state.form.values, state.form.id);
    if (!result.ok) {
      state.form.errors = result.errors;
      render('invalid');
      return result;
    }
    const savedValues = state.form.values;
    state.data = result.data;
    state.form = null;
    state.screenState = 'ready';
    if (kind === 'operation') {
      state.section = 'operations';
      state.month = savedValues.date.slice(0, 7);
      state.filter = 'all';
      state.notice = 'Операция добавлена в память демо. Показан месяц ' + state.month + '. После обновления страницы изменения исчезнут.';
    } else {
      if (kind === 'debt') state.debtDirection = savedValues.direction;
      state.notice = kind === 'repayment' ? 'Возврат отмечен в памяти демо. Счёт и бюджет не изменились.' : kind === 'debt' ? 'Долг добавлен в память демо. Счёт и бюджет не изменились.' : 'Цель обновлена в памяти демо. Счёт и бюджет не изменились.';
    }
    render();
    restoreOpener();
    return result;
  }

  function dispatch(action, value, id, source) {
    if (action === 'navigate' && ['overview', 'operations', 'goals', 'debts'].includes(value)) {
      state.section = value;
      state.form = null;
      state.notice = '';
      render('main');
    } else if (action === 'set-filter' && ['all', 'income', 'expense', 'transfer'].includes(value)) {
      state.filter = value;
      render();
      focusAction(action, value);
    } else if (action === 'reset-filter') {
      state.filter = 'all';
      render();
      focusAction('set-filter', 'all');
    } else if (action === 'screen-state' && ['ready', 'empty', 'loading', 'error'].includes(value)) {
      state.screenState = value;
      state.notice = '';
      render();
      focusAction('screen-state');
    } else if (action === 'retry') {
      state.screenState = 'ready';
      render('main');
    } else if (action === 'reset-demo') {
      state = initialState();
      state.notice = 'Демо сброшено: исходные данные D1 восстановлены.';
      render('main');
    } else if (action === 'change-month') {
      if (model.validMonth(value)) {
        state.month = value;
        state.notice = '';
      } else state.notice = 'Выберите существующий месяц. Показан прежний период.';
      render();
      focusAction('change-month');
    } else if (action === 'set-debt-direction' && ['owe', 'owedToMe'].includes(value)) {
      state.debtDirection = value;
      render();
      focusAction(action, value);
    } else if (action === 'open-operation') openForm('operation', null, source);
    else if (action === 'open-goal') openForm('goal', null, source);
    else if (action === 'edit-goal') openForm('goal-edit', id, source);
    else if (action === 'open-debt') openForm('debt', null, source);
    else if (action === 'repay-debt') openForm('repayment', id, source);
    else if (action === 'close-form') {
      state.form = null;
      render();
      restoreOpener();
    } else if (action === 'operation-type' && state.form?.kind === 'operation') {
      state.form.values.type = value;
      const selected = state.data.categories.find(item => item.id === state.form.values.categoryId && item.kind === value);
      if (!selected && value !== 'transfer') state.form.values.categoryId = state.data.categories.find(item => item.kind === value && !item.archived)?.id || '';
      state.form.errors = {};
      render();
      root.document?.querySelector('[role="dialog"] [name="type"]')?.focus();
    }
  }

  function focusAction(action, value) {
    if (!root.document) return;
    const target = Array.from(root.document.querySelectorAll('[data-action]')).find(item => item.dataset.action === action && (value === undefined || item.dataset.value === value));
    target?.focus();
  }

  function bind() {
    if (!root.document) return;
    controllerRoot = root.document.getElementById('app');
    if (!controllerRoot) return;
    controllerRoot.addEventListener('click', event => {
      const target = event.target.closest('[data-action]');
      if (!target || !controllerRoot.contains(target) || target.disabled || target.tagName === 'SELECT' || target.tagName === 'INPUT') return;
      event.preventDefault();
      dispatch(target.dataset.action, target.dataset.value, target.dataset.id, target);
    });
    controllerRoot.addEventListener('input', event => {
      const target = event.target;
      if (state.form && target.name && target.closest('[data-form]')) state.form.values[target.name] = target.value;
    });
    controllerRoot.addEventListener('change', event => {
      const target = event.target;
      if (state.form && target.closest('[data-form]')) state.form.values = { ...state.form.values, ...readForm(target.closest('[data-form]')) };
      if (target.dataset.action) dispatch(target.dataset.action, target.value, target.dataset.id, target);
    });
    controllerRoot.addEventListener('submit', event => {
      if (!event.target.matches('[data-form]')) return;
      event.preventDefault();
      submit(readForm(event.target));
    });
    root.document.addEventListener('keydown', event => {
      if (!state.form) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        dispatch('close-form');
      } else if (event.key === 'Tab') {
        const panel = root.document.querySelector('[role="dialog"]');
        if (!panel) return;
        const items = Array.from(panel.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]')).filter(item => !item.hidden && item.getClientRects().length);
        const first = items[0];
        const last = items[items.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (root.document.activeElement === first || !items.includes(root.document.activeElement))) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (root.document.activeElement === last || !items.includes(root.document.activeElement))) { event.preventDefault(); first.focus(); }
      }
    });
    render();
  }

  root.LadnoApp = Object.freeze({ get state() { return model.clone(state); }, dispatch, submit });
  if (root.document) {
    if (root.document.readyState === 'loading') root.document.addEventListener('DOMContentLoaded', bind, { once: true });
    else bind();
  }
})(globalThis);
