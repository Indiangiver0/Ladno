/* Exact KZT/2 demo model. No DOM, storage, network or binary floating-point money. */
(function (root) {
  'use strict';

  const CURRENCY = 'KZT';
  const SCALE = 2;
  const clone = value => JSON.parse(JSON.stringify(value));
  const dto = amount => ({ currency: CURRENCY, scale: SCALE, amount });

  function toMinor(amount) {
    if (typeof amount !== 'string' || amount === '-0.00' || !/^-?(?:0|[1-9]\d*)\.\d{2}$/.test(amount)) {
      throw new TypeError('Ожидалась каноническая денежная строка KZT/2');
    }
    const negative = amount[0] === '-';
    const digits = (negative ? amount.slice(1) : amount).replace('.', '');
    return (negative ? -1n : 1n) * BigInt(digits);
  }

  function fromMinor(value) {
    if (typeof value !== 'bigint') throw new TypeError('Ожидались целые минимальные единицы');
    const negative = value < 0n;
    const digits = (negative ? -value : value).toString().padStart(3, '0');
    return (negative ? '-' : '') + digits.slice(0, -2) + '.' + digits.slice(-2);
  }

  function moneyMinor(value) {
    if (!value || value.currency !== CURRENCY || value.scale !== SCALE) {
      throw new TypeError('В демо поддерживается только KZT с точностью 2');
    }
    return toMinor(value.amount);
  }

  function formatMoney(amount) {
    // Validate the canonical value; grouping and comma replacement are string-only.
    toMinor(amount);
    const negative = amount[0] === '-';
    const parts = (negative ? amount.slice(1) : amount).split('.');
    return (negative ? '−' : '') + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ',' + parts[1] + ' KZT';
  }

  function parseAmount(raw, allowZero) {
    if (typeof raw !== 'string' || raw === '') return { ok: false, error: 'Введите сумму' };
    if (!/^-?\d+(?:[.,]\d+)?$/.test(raw)) {
      return { ok: false, error: 'Введите число без пробелов, например 1200,50' };
    }
    const parts = raw.replace(',', '.').split('.');
    if ((parts[1] || '').length > SCALE) return { ok: false, error: 'Не более 2 знаков после запятой' };
    if (raw[0] === '-') {
      return { ok: false, error: allowZero ? 'Сумма не может быть отрицательной' : 'Введите сумму больше 0' };
    }
    const whole = parts[0].replace(/^0+(?=\d)/, '');
    const amount = whole + '.' + (parts[1] || '').padEnd(SCALE, '0');
    const minor = toMinor(amount);
    if (!allowZero && minor === 0n) return { ok: false, error: 'Введите сумму больше 0' };
    return { ok: true, amount, minor };
  }

  function validMonth(value) {
    return typeof value === 'string' && /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(value);
  }

  function validDate(value) {
    if (typeof value !== 'string' || !/^(?!0000)\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(value)) return false;
    const year = BigInt(value.slice(0, 4));
    const leap = year % 400n === 0n || (year % 4n === 0n && year % 100n !== 0n);
    const max = { '01': '31', '02': leap ? '29' : '28', '03': '31', '04': '30', '05': '31', '06': '30', '07': '31', '08': '31', '09': '30', '10': '31', '11': '30', '12': '31' };
    return value.slice(8) <= max[value.slice(5, 7)];
  }

  function operationsFor(data, month, filter) {
    return data.operations.filter(operation => operation.date.slice(0, 7) === month && (filter === 'all' || !filter || operation.type === filter))
      .slice().sort((a, b) => b.date.localeCompare(a.date) || a.id.localeCompare(b.id));
  }

  function percentage(numerator, denominator, cap) {
    if (denominator === 0n) return numerator > 0n ? '100' : '0';
    let percent = (200n * numerator + denominator) / (2n * denominator);
    if (cap && percent > 100n) percent = 100n;
    return (percent < 0n ? 0n : percent).toString();
  }

  function summary(data, month) {
    const operations = operationsFor(data, month, 'all');
    let income = 0n;
    let expense = 0n;
    const spentByCategory = new Map();
    for (const operation of operations) {
      const value = moneyMinor(operation.amount);
      if (operation.type === 'income') income += value;
      if (operation.type === 'expense') {
        expense += value;
        spentByCategory.set(operation.categoryId, (spentByCategory.get(operation.categoryId) || 0n) + value);
      }
    }
    const budget = data.budget && data.budget.month === month ? data.budget : null;
    let planned = null;
    const plans = new Map();
    if (budget) {
      if (budget.currency !== CURRENCY || budget.scale !== SCALE) throw new TypeError('Валюта бюджета не совпадает');
      planned = 0n;
      if (budget.mode === 'category') {
        for (const plan of budget.plans) {
          if (plans.has(plan.categoryId)) throw new TypeError('Категория бюджета повторяется');
          const value = moneyMinor(plan.planned);
          plans.set(plan.categoryId, value);
          planned += value;
        }
      } else if (budget.mode === 'total') {
        planned = moneyMinor(budget.planned);
      } else throw new TypeError('Неизвестный режим бюджета');
    }
    const categories = data.categories.filter(category => category.kind === 'expense').map(category => {
      const spent = spentByCategory.get(category.id) || 0n;
      const limit = plans.has(category.id) ? plans.get(category.id) : null;
      return { id: category.id, name: category.name, planned: limit === null ? null : fromMinor(limit), spent: fromMinor(spent), remaining: limit === null ? null : fromMinor(limit - spent), percent: limit === null ? '0' : percentage(spent, limit, true) };
    });
    const remaining = planned === null ? null : planned - expense;
    return { income: fromMinor(income), expense: fromMinor(expense), planned: planned === null ? null : fromMinor(planned), remaining: remaining === null ? null : fromMinor(remaining), categories, budgetPercent: planned === null ? '0' : percentage(expense, planned, true), status: remaining === null ? 'unset' : remaining < 0n ? 'over' : 'within' };
  }

  function goalProgress(goal) {
    const target = moneyMinor(goal.target);
    const accumulated = moneyMinor(goal.accumulated);
    if (target <= 0n || accumulated < 0n) throw new TypeError('Неверные суммы цели');
    return { percent: percentage(accumulated, target, false), barPercent: percentage(accumulated, target, true), reached: accumulated >= target };
  }

  function debtTotal(data, direction) {
    return fromMinor(data.debts.filter(debt => debt.direction === direction).reduce((total, debt) => total + moneyMinor(debt.outstanding), 0n));
  }

  function uniqueId(data, collection, prefix) {
    let suffix = 1n;
    while (data[collection].some(item => item.id === prefix + suffix)) suffix += 1n;
    return prefix + suffix;
  }

  function applyForm(data, kind, values, id) {
    const errors = {};
    const parsed = {};
    const amountField = (name, allowZero) => {
      parsed[name] = parseAmount(values[name], allowZero);
      if (!parsed[name].ok) errors[name] = parsed[name].error;
    };
    const requireLabel = (name, message) => { if (typeof values[name] !== 'string' || !values[name].trim()) errors[name] = message; };
    const requireDate = (name, optional) => { if ((!optional || values[name]) && !validDate(values[name])) errors[name] = 'Укажите существующую дату в формате ГГГГ-ММ-ДД'; };
    let selected;
    if (kind === 'operation') {
      if (!['income', 'expense', 'transfer'].includes(values.type)) errors.type = 'Выберите тип операции';
      amountField('amount', false);
      requireDate('date', false);
      const account = data.accounts.find(item => item.id === values.accountId && !item.archived);
      if (!account) errors.accountId = 'Выберите счёт';
      else if (account.currency !== CURRENCY || account.scale !== SCALE) errors.accountId = 'Валюта счёта должна быть KZT с точностью 2';
      if (values.type === 'transfer') {
        // D1 deliberately has one account. Creating a second account is outside this prototype.
        errors.type = 'Для перевода нужны два разных счёта. В демо доступен один счёт.';
      } else {
        const category = data.categories.find(item => item.id === values.categoryId && !item.archived && item.kind === values.type);
        if (!category) errors.categoryId = 'Выберите категорию для этого типа операции';
      }
    } else if (kind === 'goal') {
      requireLabel('name', 'Введите название цели');
      amountField('target', false);
      amountField('accumulated', true);
    } else if (kind === 'goal-edit') {
      selected = data.goals.find(item => item.id === id);
      if (!selected) errors.accumulated = 'Цель не найдена. Закройте форму и откройте её снова.';
      amountField('accumulated', true);
    } else if (kind === 'debt') {
      if (!['owe', 'owedToMe'].includes(values.direction)) errors.direction = 'Выберите направление долга';
      requireLabel('label', 'Введите имя или метку');
      amountField('amount', false);
      requireDate('dueDate', true);
    } else if (kind === 'repayment') {
      selected = data.debts.find(item => item.id === id);
      amountField('amount', false);
      requireDate('date', false);
      if (!selected) errors.amount = 'Долг не найден. Закройте форму и откройте её снова.';
      else if (parsed.amount.ok && parsed.amount.minor > moneyMinor(selected.outstanding)) errors.amount = 'Возврат не может быть больше остатка долга: ' + formatMoney(selected.outstanding.amount);
      // Snapshot state has no historical date picker; a later return must not alter its past.
      if (validDate(values.date) && values.date > data.metadata.snapshotDate) errors.date = 'Для состояния на 30.10.2026 дата возврата должна быть не позже 30.10.2026';
    } else return { ok: false, errors: { form: 'Неизвестная форма' } };
    if (Object.keys(errors).length) return { ok: false, errors };
    const next = clone(data);
    if (kind === 'operation') {
      next.operations.push({ id: uniqueId(next, 'operations', 'demo-operation-'), revision: 1, type: values.type, date: values.date, accountId: values.accountId, categoryId: values.categoryId, amount: dto(parsed.amount.amount), note: values.note || '' });
    } else if (kind === 'goal') {
      next.goals.push({ id: uniqueId(next, 'goals', 'demo-goal-'), revision: 1, name: values.name.trim(), snapshotDate: data.metadata.snapshotDate, target: dto(parsed.target.amount), accumulated: dto(parsed.accumulated.amount), contributionsInMonth: [], ledgerEffect: 'none' });
    } else if (kind === 'goal-edit') {
      next.goals.find(item => item.id === id).accumulated = dto(parsed.accumulated.amount);
    } else if (kind === 'debt') {
      next.debts.push({ id: uniqueId(next, 'debts', 'demo-debt-'), revision: 1, direction: values.direction, label: values.label.trim(), snapshotDate: data.metadata.snapshotDate, outstanding: dto(parsed.amount.amount), dueDate: values.dueDate || null, repaymentsInMonth: [], historyState: 'not-provided', ledgerEffect: 'none' });
    } else if (kind === 'repayment') {
      const debt = next.debts.find(item => item.id === id);
      debt.outstanding = dto(fromMinor(moneyMinor(debt.outstanding) - parsed.amount.minor));
      debt.demoRepayments = (debt.demoRepayments || []).concat({ date: values.date, amount: dto(parsed.amount.amount), ledgerEffect: 'none' });
    }
    return { ok: true, data: next, errors: {} };
  }

  root.LadnoModel = Object.freeze({ currency: CURRENCY, scale: SCALE, clone, toMinor, fromMinor, formatMoney, parseAmount, validDate, validMonth, summary, operationsFor, goalProgress, debtTotal, applyForm });
})(globalThis);
