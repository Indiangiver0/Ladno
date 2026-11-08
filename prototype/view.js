(function (root) {
  'use strict';

  const icons = {
    overview: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    operations: '<path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4"/>',
    goals: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
    debts: '<path d="M8 12a4 4 0 0 1 4-4h5a4 4 0 0 1 0 8h-3M16 12a4 4 0 0 1-4 4H7a4 4 0 0 1 0-8h3"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18"/>',
    arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
    up: '<path d="M7 17 17 7M7 7h10v10"/>',
    down: '<path d="M7 7 17 17M7 17h10V7"/>',
    wallet: '<path d="M20 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h15V8H5a2 2 0 0 1 0-4"/><path d="M20 12h-5v5h5"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.01"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    leaf: '<path d="M20 4c0 10-3 16-9 16a7 7 0 0 1-7-7C4 7 10 4 20 4ZM4 20l11-11"/>',
    empty: '<path d="m4 8 8-5 8 5v12H4V8Z"/><path d="M4 12h5l1 3h4l1-3h5"/>',
    error: '<path d="m12 3 10 18H2L12 3Z"/><path d="M12 9v5m0 3v.01"/>',
    sliders: '<path d="M4 7h16M4 17h16"/><circle cx="8" cy="7" r="2" fill="currentColor"/><circle cx="16" cy="17" r="2" fill="currentColor"/>'
  };
  const sections = { overview: 'Обзор', operations: 'Операции', goals: 'Цели', debts: 'Долги' };
  const typeNames = { all: 'Все', income: 'Доходы', expense: 'Расходы', transfer: 'Переводы' };
  const monthNames = { '01': 'Январь', '02': 'Февраль', '03': 'Март', '04': 'Апрель', '05': 'Май', '06': 'Июнь', '07': 'Июль', '08': 'Август', '09': 'Сентябрь', '10': 'Октябрь', '11': 'Ноябрь', '12': 'Декабрь' };
  const e = (value) => String(value == null ? '' : value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const icon = (name, size) => '<svg width="' + (size || 18) + '" height="' + (size || 18) + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (icons[name] || icons.info) + '</svg>';
  const money = (amount) => amount == null ? '—' : e(root.LadnoModel.formatMoney(amount));
  const dateLabel = (date) => date ? e(date.split('-').reverse().join('.')) : 'Без срока';
  const monthLabel = (month) => e((monthNames[month.slice(5, 7)] || month.slice(5, 7)) + ' ' + month.slice(0, 4));
  const action = (name, label, options) => {
    const o = options || {};
    return '<button type="button" class="' + e(o.className || 'text-button') + '" data-action="' + e(name) + '"' + (o.value != null ? ' data-value="' + e(o.value) + '"' : '') + (o.id != null ? ' data-id="' + e(o.id) + '"' : '') + (o.disabled ? ' disabled' : '') + '>' + (o.icon ? icon(o.icon, o.iconSize) : '') + label + (o.arrow ? icon('arrow', 15) : '') + '</button>';
  };
  const progress = (value) => '<div class="progress-track" aria-hidden="true"><div class="progress-fill" style="--progress:' + e(value) + '%"></div></div>';

  function navigation(state) {
    return '<aside class="sidebar"><div class="brand"><span class="brand-mark" aria-hidden="true">л</span><span>Ладно</span></div><p class="brand-caption">Личные финансы, спокойно.</p><nav class="main-nav" aria-label="Разделы">' + Object.keys(sections).map((id, index) => '<button type="button" class="nav-item" data-action="navigate" data-value="' + id + '"' + (state.section === id ? ' aria-current="page"' : '') + '>' + icon(id, 19) + '<span>' + sections[id] + '</span><span class="nav-index" aria-hidden="true">0' + (index + 1) + '</span></button>').join('') + '</nav><div class="sidebar-bottom"><div class="sidebar-note"><strong>Ваш месяц — в одном месте</strong>Деньги, планы и маленькие шаги к большому.</div><p class="sidebar-version">ЛОКАЛЬНЫЙ ПРОТОТИП · D1</p></div></aside>';
  }

  function heading(state) {
    const monthly = state.section === 'overview' || state.section === 'operations';
    const descriptions = { overview: 'Всё важное о вашем месяце.', operations: 'У каждой суммы — своя история.', goals: 'Большие планы начинаются с маленьких шагов.', debts: 'Договорённости, о которых легко помнить.' };
    const cta = { overview: ['open-operation', 'Добавить операцию'], operations: ['open-operation', 'Добавить операцию'], goals: ['open-goal', 'Создать цель'], debts: ['open-debt', 'Записать долг'] }[state.section];
    return '<div class="topline"><div class="topline-path"><span>Личное пространство</span><span aria-hidden="true">/</span><span>' + sections[state.section] + '</span></div><span class="demo-pill"><span class="demo-dot"></span>Демо без сохранения</span></div><header class="page-heading"><div><h1>' + sections[state.section] + '</h1><p class="page-description">' + descriptions[state.section] + '<br>' + (monthly ? monthLabel(state.month) : 'На 30.10.2026') + ' · Демо · KZT · 2 знака</p></div><div class="heading-actions">' + (monthly ? '<label class="period-control">' + icon('calendar', 17) + '<input type="month" data-action="change-month" value="' + e(state.month) + '" aria-label="Месяц"></label>' : '') + action(cta[0], e(cta[1]), { className: 'primary', icon: 'plus' }) + '</div></header>';
  }

  function accountBanner(state) {
    const account = state.data.accounts[0];
    return '<div class="account-banner"><div class="account-name"><span class="account-icon">' + icon('wallet', 16) + '</span>' + e(account.name) + ' <span class="muted">· KZT</span></div><div class="account-unknown">' + icon('info', 14) + '<span>Начальный баланс не задан · баланс счёта неизвестен</span></div></div>';
  }

  function independentNote() {
    return '<p class="independent-note">' + icon('info', 14) + '<span>Изменение учёта целей и долгов не меняет счёт и бюджет. Два направления долгов учитываются отдельно.</span></p>';
  }

  function goalSummary(goal) {
    if (!goal) return '<p class="muted">Целей пока нет.</p>';
    const p = root.LadnoModel.goalProgress(goal);
    return '<p class="goal-summary-title">' + e(goal.name) + '</p><div class="goal-summary-amounts"><span>Накоплено ' + money(goal.accumulated.amount) + '<br>из ' + money(goal.target.amount) + '</span><strong class="goal-percent">' + e(p.percent) + '%</strong></div>' + progress(p.barPercent) + '<p class="goal-meta">Ручной учёт на 30.10.2026</p>';
  }

  function overview(state) {
    const s = root.LadnoModel.summary(state.data, state.month);
    const remaining = s.remaining == null ? '—' : root.LadnoModel.formatMoney(s.remaining);
    const heroAmount = e(remaining).replace(/ KZT$/, ' <span class="currency">KZT</span>');
    const status = s.status === 'over' ? 'Превышен' : s.status === 'unset' ? 'Не настроен' : 'В пределах';
    return accountBanner(state) + '<div class="overview-top"><section class="budget-hero" aria-labelledby="budget-title"><div class="card-title-row"><h2 id="budget-title">Бюджет месяца</h2><span class="status-chip' + (s.status === 'over' ? ' over' : '') + '">' + status + '</span></div><p class="hero-label">Остаток бюджета</p><p class="hero-amount">' + heroAmount + '</p><div class="hero-breakdown"><div><span>План бюджета</span><strong>' + money(s.planned) + '</strong></div><div><span>Потрачено</span><strong>' + money(s.expense) + '</strong></div></div>' + progress(s.budgetPercent) + '<p class="hero-footnote">' + (s.planned == null ? 'На этот месяц бюджет не настроен. Известные расходы показаны отдельно.' : 'Остаток бюджета — разница плана и расходов месяца, не баланс счёта.') + '</p></section><div class="monthly-metrics"><section class="metric-card"><div class="metric-top"><span>Доходы месяца</span><span class="metric-icon">' + icon('down', 16) + '</span></div><p class="metric-amount">' + money(s.income) + '</p><p class="metric-period">' + monthLabel(state.month) + ' · по дате операции</p></section><section class="metric-card"><div class="metric-top"><span>Расходы месяца</span><span class="metric-icon expense">' + icon('up', 16) + '</span></div><p class="metric-amount">' + money(s.expense) + '</p><p class="metric-period">' + monthLabel(state.month) + ' · по дате операции</p></section></div></div><section class="card"><div class="card-title-row"><h2>Расходы по категориям</h2><span class="subtle-label">Факт / план · KZT</span></div>' + (s.categories.length ? '<div class="categories">' + s.categories.map((c, index) => '<div class="category"><p class="category-heading"><span class="category-dot tint-' + index + '"></span>' + e(c.name) + '</p><p class="category-amounts"><strong>' + money(c.spent) + '</strong><span>/ ' + money(c.planned) + '</span></p>' + progress(c.percent) + '<p class="category-remaining">' + (c.remaining == null ? 'План не задан' : 'Остаток: ' + money(c.remaining)) + '</p></div>').join('') + '</div>' : '<p class="muted">На этот месяц планы категорий не заданы.</p>') + '</section><div class="overview-bottom"><section class="card"><div class="card-title-row"><h2>Ближе к цели</h2>' + action('navigate', 'Все цели', { value: 'goals', arrow: true }) + '</div>' + goalSummary(state.data.goals[0]) + '</section><section class="card debt-summary"><div class="card-title-row"><h2>Долги</h2><span class="subtle-label">На 30.10.2026</span></div><div class="debt-summary-row"><span>Я должен</span><strong>' + money(root.LadnoModel.debtTotal(state.data, 'owe')) + '</strong></div><div class="debt-summary-row"><span>Мне должны</span><strong>' + money(root.LadnoModel.debtTotal(state.data, 'owedToMe')) + '</strong></div>' + action('navigate', 'Все долги', { value: 'debts', arrow: true }) + '</section></div>' + independentNote();
  }

  function operationRows(state, operations) {
    return operations.map((op) => {
      const category = state.data.categories.find((c) => c.id === op.categoryId);
      const account = state.data.accounts.find((a) => a.id === op.accountId);
      const income = op.type === 'income';
      const type = income ? 'Доход' : op.type === 'expense' ? 'Расход' : 'Перевод';
      return '<tr><td><div class="operation-name"><span class="operation-symbol' + (income ? ' income' : '') + '">' + icon(income ? 'down' : 'up', 18) + '</span><div><p class="operation-title">' + e(category ? category.name : type) + '</p><p class="operation-type">' + type + '</p><p class="mobile-row-meta">' + dateLabel(op.date) + ' · ' + e(account ? account.name : '') + '</p>' + (op.note ? '<span class="operation-note">' + e(op.note) + '</span>' : '') + '</div></div></td><td>' + dateLabel(op.date) + '</td><td>' + e(account ? account.name : '') + '</td><td' + (income ? ' class="amount-income"' : '') + '>' + (income ? '+ ' : op.type === 'expense' ? '− ' : '') + money(op.amount.amount) + '</td></tr>';
    }).join('');
  }

  function operationsView(state) {
    const s = root.LadnoModel.summary(state.data, state.month);
    const operations = root.LadnoModel.operationsFor(state.data, state.month, state.filter);
    const filters = Object.keys(typeNames).map((type) => '<button type="button" class="filter-button" data-action="set-filter" data-value="' + type + '" aria-pressed="' + (state.filter === type) + '">' + typeNames[type] + '</button>').join('');
    return '<section class="card operations-metrics" aria-label="Итоги месяца"><div class="compact-metric"><span>Доходы месяца</span><strong>' + money(s.income) + '</strong></div><div class="compact-metric"><span>Расходы месяца</span><strong>' + money(s.expense) + '</strong></div><div class="compact-metric"><span>Остаток бюджета</span><strong>' + money(s.remaining) + '</strong></div></section><section class="card operations-card"><div class="operations-toolbar"><h2>История операций</h2><div class="filter-list" aria-label="Тип операций">' + filters + '</div></div><p class="filter-note">Фильтр: ' + typeNames[state.filter] + ' · ' + monthLabel(state.month) + '. Фильтр меняет список, а не бюджет месяца.</p>' + (operations.length ? '<table class="operations-table"><thead><tr><th scope="col">Операция</th><th scope="col">Дата</th><th scope="col">Счёт</th><th scope="col">Сумма · KZT</th></tr></thead><tbody>' + operationRows(state, operations) + '</tbody></table><p class="table-footer">Показано операций: ' + operations.length + ' · Только ручной учёт в памяти</p>' : '<div class="state-card" style="border:0"><div class="state-icon">' + icon('empty', 26) + '</div><h2>' + (state.filter !== 'all' ? 'По этим фильтрам ничего нет' : 'Пока нет операций') + '</h2><p>' + (state.filter !== 'all' ? 'Попробуйте другой тип или вернитесь ко всем операциям месяца.' : 'Добавьте первую операцию этого месяца. Она появится здесь и в итогах.') + '</p>' + (state.filter !== 'all' ? action('reset-filter', 'Сбросить фильтры', { className: 'secondary' }) : action('open-operation', 'Добавить операцию', { className: 'primary', icon: 'plus' })) + '</div>') + '</section><p class="independent-note">' + icon('info', 14) + '<span>Основной · KZT · начальный баланс не задан. Доходы минус расходы не означают баланс счёта.</span></p>';
  }

  function goalsView(state) {
    if (!state.data.goals.length) return emptyView(state);
    return '<div class="entity-grid">' + state.data.goals.map((goal) => {
      const p = root.LadnoModel.goalProgress(goal);
      return '<article class="card goal-card"><div class="entity-icon">' + icon('goals', 23) + '</div><h2 class="entity-name">' + e(goal.name) + '</h2><p class="entity-description">' + (p.reached ? 'Цель достигнута. Хороший повод порадоваться.' : 'Спокойно движемся к задуманному.') + '</p><span class="subtle-label">Накоплено</span><p class="goal-large-amount">' + money(goal.accumulated.amount) + '</p><p class="goal-target">из ' + money(goal.target.amount) + '</p><div class="goal-progress-line"><span>Прогресс</span><strong>' + e(p.percent) + '%</strong></div>' + progress(p.barPercent) + '<div class="entity-actions"><span class="subtle-label">На 30.10.2026</span>' + action('edit-goal', 'Изменить накопленное', { id: goal.id }) + '</div><p class="goal-meta">Ручной учёт на 30.10.2026. Изменения не создают операций.</p></article>';
    }).join('') + '</div>' + independentNote();
  }

  function debtsView(state) {
    const debts = state.data.debts.filter((d) => d.direction === state.debtDirection);
    return '<div class="debt-directions" aria-label="Направление долга">' + ['owe', 'owedToMe'].map((direction) => '<button type="button" class="debt-direction" data-action="set-debt-direction" data-value="' + direction + '" aria-pressed="' + (direction === state.debtDirection) + '"><span>' + (direction === 'owe' ? 'Я должен' : 'Мне должны') + '</span><strong>' + money(root.LadnoModel.debtTotal(state.data, direction)) + '</strong></button>').join('') + '</div>' + (debts.length ? '<div class="entity-grid">' + debts.map((debt) => '<article class="card goal-card"><div class="card-title-row"><div class="entity-icon" style="margin-bottom:0">' + icon('debts', 23) + '</div><span class="status-chip">' + (debt.outstanding.amount === '0.00' ? 'Погашен' : 'Без процентов') + '</span></div><h2 class="entity-name">' + e(debt.label) + '</h2><p class="subtle-label">' + (debt.direction === 'owe' ? 'Я должен' : 'Мне должны') + '</p><p class="debt-amount-label">Остаток долга</p><p class="debt-amount">' + money(debt.outstanding.amount) + '</p><div class="debt-details"><span>Срок</span><span>' + dateLabel(debt.dueDate) + '</span></div><p class="history-note">' + (debt.historyState === 'not-provided' ? 'Исходная история возвратов не предоставлена. Учитываем известный остаток.' : 'Ручной учёт долга в демо.') + '</p><div class="entity-actions"><span class="subtle-label">На 30.10.2026</span>' + action('repay-debt', debt.outstanding.amount === '0.00' ? 'Долг погашен' : 'Записать возврат', { id: debt.id, disabled: debt.outstanding.amount === '0.00' }) + '</div></article>').join('') + '</div>' : '<div class="state-card"><div class="state-icon">' + icon('debts', 27) + '</div><h2>В этом направлении долгов нет</h2><p>Здесь будут ваши ручные записи. Без процентов и взаимозачёта.</p>' + action('open-debt', 'Записать долг', { className: 'primary', icon: 'plus' }) + '</div>') + independentNote();
  }

  function emptyView(state) {
    const copy = {
      overview: ['Бюджет не настроен', 'План бюджета и остаток — неизвестны, а не равны нулю. Это образец пустого состояния обзора.', 'retry', 'Показать демоданные'],
      operations: ['Пока нет операций', 'Начните с одной записи дохода или расхода. Это образец пустого месяца.', 'open-operation', 'Добавить операцию'],
      goals: ['Здесь начнутся ваши планы', 'Создайте цель и отмечайте накопленное вручную. Учёт цели не меняет бюджет.', 'open-goal', 'Создать цель'],
      debts: ['Пока нет долгов', 'Запишите договорённость, чтобы помнить сумму и срок. Без процентов и скрытых движений денег.', 'open-debt', 'Записать долг']
    }[state.section];
    return '<section class="state-card"><div class="state-icon">' + icon(state.section === 'goals' ? 'leaf' : 'empty', 27) + '</div><p class="state-caption">Демо-состояние · пусто</p><h2>' + copy[0] + '</h2><p>' + copy[1] + '</p>' + action(copy[2], copy[3], { className: 'primary', icon: copy[2] === 'retry' ? null : 'plus' }) + '</section>';
  }

  function stateView(state) {
    if (state.screenState === 'empty') return emptyView(state);
    if (state.screenState === 'loading') return '<section class="loading-section" aria-busy="true" aria-label="Демонстрация загрузки"><p class="loading-heading">Демо-состояние · загрузка</p><div class="skeleton-grid" aria-hidden="true"><div class="skeleton-card"><div class="skeleton-line"></div><div class="skeleton-line wide"></div><div class="skeleton-line short"></div></div><div class="skeleton-card"><div class="skeleton-line"></div><div class="skeleton-line wide"></div></div></div><p class="loading-footnote">Образец состояния. Сетевых запросов нет; неизвестные данные не заменяются нулями.</p>' + action('retry', 'Показать демоданные') + '</section>';
    return '<section class="state-card"><div class="state-icon error">' + icon('error', 27) + '</div><p class="state-caption">Демо-состояние · ошибка</p><h2>Не удалось показать данные</h2><p>Это образец ошибки просмотра. Записи в памяти сохранены. Повторите, чтобы вернуться к данным.</p>' + action('retry', 'Повторить', { className: 'secondary' }) + '</section>';
  }

  function toolbar(state) {
    return '<div class="demo-toolbar"><div class="demo-toolbar-caption">' + icon('sliders', 15) + '<span><strong>Просмотр прототипа</strong> · без сетевых запросов</span></div><div class="demo-toolbar-controls"><label><span class="sr-only">Состояние экрана</span><select data-action="screen-state" aria-label="Состояние экрана">' + [['ready', 'Данные'], ['empty', 'Пустое состояние'], ['loading', 'Загрузка'], ['error', 'Ошибка']].map((pair) => '<option value="' + pair[0] + '"' + (state.screenState === pair[0] ? ' selected' : '') + '>' + pair[1] + '</option>').join('') + '</select></label><button type="button" class="reset-button" data-action="reset-demo">Сбросить демо</button></div></div><p class="demo-currency-note">Синтетические данные D1 · KZT / 2 знака — договорённость демо. Изменения только в памяти; обновление страницы и сброс возвращают исходный D1.</p>';
  }

  function field(form, name, label, opts) {
    const o = opts || {};
    const error = form.errors && form.errors[name];
    const value = form.values[name] == null ? '' : form.values[name];
    const hintId = name + '-hint';
    const errorId = 'error-' + name;
    const attrs = ' id="field-' + e(name) + '" name="' + e(name) + '"' + (error ? ' aria-invalid="true"' : '') + ((o.hint || error) ? ' aria-describedby="' + (o.hint ? hintId : '') + (o.hint && error ? ' ' : '') + (error ? errorId : '') + '"' : '');
    let input;
    if (o.options) input = '<select' + attrs + (o.action ? ' data-action="' + e(o.action) + '"' : '') + '>' + o.options.map((pair) => '<option value="' + e(pair[0]) + '"' + (String(value) === String(pair[0]) ? ' selected' : '') + '>' + e(pair[1]) + '</option>').join('') + '</select>';
    else if (o.textarea) input = '<textarea' + attrs + ' rows="2" placeholder="' + e(o.placeholder || '') + '">' + e(value) + '</textarea>';
    else input = '<input type="text"' + attrs + ' value="' + e(value) + '"' + (o.money ? ' inputmode="decimal"' : '') + ' autocomplete="off"' + (o.placeholder ? ' placeholder="' + e(o.placeholder) + '"' : '') + '>';
    return '<div class="field"><label for="field-' + e(name) + '">' + e(label) + '</label>' + input + (o.hint ? '<p class="field-hint" id="' + e(hintId) + '">' + e(o.hint) + '</p>' : '') + (error ? '<p class="field-error" id="' + e(errorId) + '" role="alert">' + e(error) + '</p>' : '') + '</div>';
  }

  function formView(state) {
    const form = state.form;
    if (!form) return '';
    const kind = form.kind;
    const titles = { operation: 'Новая операция', goal: 'Новая цель', 'goal-edit': 'Изменить накопленное', debt: 'Записать долг', repayment: 'Записать возврат' };
    let fields = '';
    let disabled = false;
    let submit = 'Сохранить в демо';
    const amountHint = 'Положительная сумма, не более 2 знаков после запятой. Без пробелов.';
    if (kind === 'operation') {
      const transfer = form.values.type === 'transfer';
      disabled = transfer;
      fields += field(form, 'type', 'Тип операции', { options: [['expense', 'Расход'], ['income', 'Доход'], ['transfer', 'Перевод']], action: 'operation-type' });
      fields += field(form, 'amount', 'Сумма · KZT', { money: true, placeholder: '0,00', hint: amountHint });
      fields += field(form, 'date', 'Дата операции', { placeholder: '2026-10-30', hint: 'Формат ГГГГ-ММ-ДД. Сумма попадёт в месяц этой даты.' });
      fields += field(form, 'accountId', transfer ? 'Со счёта' : 'Счёт', { options: [['', 'Выберите счёт']].concat(state.data.accounts.map((a) => [a.id, a.name + ' · ' + a.currency])) });
      if (transfer) fields += '<div class="form-info warning" role="status">Для перевода нужны два разных счёта. В демо доступен один счёт. Перевод сохранить нельзя.</div>';
      else fields += field(form, 'categoryId', 'Категория', { options: [['', 'Выберите категорию']].concat(state.data.categories.filter((c) => c.kind === form.values.type).map((c) => [c.id, c.name])) });
      fields += field(form, 'note', 'Заметка · необязательно', { textarea: true, placeholder: 'На что или откуда' });
      submit = 'Добавить операцию';
    } else if (kind === 'goal') {
      fields += '<div class="form-info">Ручная отметка накопления. Она не создаёт доход, расход или перевод.</div>';
      fields += field(form, 'name', 'Название цели', { placeholder: 'Например, отпуск' });
      fields += field(form, 'target', 'Целевая сумма · KZT', { money: true, placeholder: '0,00', hint: amountHint });
      fields += field(form, 'accumulated', 'Уже накоплено · KZT', { money: true, placeholder: '0,00', hint: 'Ноль допустим. Суммы не округляются.' });
      submit = 'Создать цель';
    } else if (kind === 'goal-edit') {
      const goal = state.data.goals.find((g) => g.id === form.id);
      fields += '<div class="form-info"><strong>' + e(goal ? goal.name : '') + '</strong><br>Целевая сумма: ' + money(goal ? goal.target.amount : null) + '<br>Изменится только учёт цели, а не счёт или бюджет.</div>';
      fields += field(form, 'accumulated', 'Накоплено · KZT', { money: true, placeholder: '0,00', hint: 'Укажите полную накопленную сумму. Ноль допустим.' });
      submit = 'Изменить накопленное';
    } else if (kind === 'debt') {
      fields += '<div class="form-info">Запись известного остатка. Без процентов, взаимозачёта и автоматических операций по счёту.</div>';
      fields += field(form, 'direction', 'Направление', { options: [['owe', 'Я должен'], ['owedToMe', 'Мне должны']] });
      fields += field(form, 'label', 'Имя или метка', { placeholder: 'Например, Пример В' });
      fields += field(form, 'amount', 'Остаток долга · KZT', { money: true, placeholder: '0,00', hint: amountHint });
      fields += field(form, 'dueDate', 'Срок · необязательно', { placeholder: '2026-11-15', hint: 'Формат ГГГГ-ММ-ДД. Можно оставить пустым.' });
      submit = 'Записать долг';
    } else if (kind === 'repayment') {
      const debt = state.data.debts.find((d) => d.id === form.id);
      fields += '<div class="form-info"><strong>' + e(debt ? debt.label : '') + '</strong><br>Остаток: ' + money(debt ? debt.outstanding.amount : null) + '<br>Возврат уменьшит только остаток долга.</div>';
      fields += field(form, 'amount', 'Сумма возврата · KZT', { money: true, placeholder: '0,00', hint: 'Больше нуля и не больше остатка. Не более 2 знаков после запятой.' });
      fields += field(form, 'date', 'Дата возврата', { placeholder: '2026-10-30', hint: 'Формат ГГГГ-ММ-ДД. Возврат отмечается на 30.10.2026; выберите дату не позже этой даты.' });
      submit = 'Записать возврат';
    }
    const generalError = form.errors && (form.errors._form || form.errors.form);
    return '<div class="form-overlay"><button type="button" class="form-backdrop" data-action="close-form" tabindex="-1" aria-label="Закрыть форму"></button><section class="form-panel" role="dialog" aria-modal="true" aria-labelledby="form-title"><div class="form-heading"><h2 id="form-title" tabindex="-1">' + titles[kind] + '</h2><button type="button" class="icon-button" data-action="close-form" aria-label="Закрыть форму">' + icon('close', 18) + '</button></div><p class="form-subtitle">Демо · KZT · 2 знака после запятой<br>Изменения останутся только до обновления страницы.</p><form data-form="' + e(kind) + '" novalidate>' + fields + (generalError ? '<p class="field-error" role="alert">' + e(generalError) + '</p>' : '') + '<div class="form-actions">' + action('close-form', 'Отмена', { className: 'secondary' }) + '<button type="submit" class="primary"' + (disabled ? ' disabled' : '') + '>' + e(submit) + '</button></div></form><p class="form-footnote">Демонстрационные данные. Долговременного сохранения нет.</p></section></div>';
  }

  function render(state) {
    const app = root.document.getElementById('app');
    const views = { overview, operations: operationsView, goals: goalsView, debts: debtsView };
    app.innerHTML = '<div class="app-shell">' + navigation(state) + '<main class="workspace" id="main-content" tabindex="-1">' + heading(state) + (state.notice ? '<div class="notice" role="status" aria-live="polite">' + icon('check', 17) + '<span>' + e(state.notice) + '</span></div>' : '') + (state.screenState === 'ready' ? views[state.section](state) : stateView(state)) + toolbar(state) + '</main></div>' + formView(state);
  }

  root.LadnoView = Object.freeze({ render });
})(typeof window !== 'undefined' ? window : globalThis);
