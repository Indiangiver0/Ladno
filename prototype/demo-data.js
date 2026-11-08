/* D1 1.0.0 — exact offline copy of design/demo-data.json; memory-only prototype. */
'use strict';
globalThis.LadnoDemoData = {
  "fixtureId": "D1",
  "fixtureVersion": "1.0.0",
  "metadata": {
    "label": "Демо",
    "description": "Концепция / демонстрационные данные. Все сведения синтетические.",
    "purpose": "design-fixture",
    "isRuntimeBackup": false,
    "source": "docs/product/SCREENS.md, APP-001-PROD-AC v1, D1",
    "month": "2026-10",
    "period": {
      "startInclusive": "2026-10-01",
      "endExclusive": "2026-11-01"
    },
    "snapshotDate": "2026-10-30",
    "currency": "KZT",
    "scale": 2,
    "currencyNote": "KZT и точность 2 выбраны только для макета: это не валюта пользователя и не утверждение законодательного правила.",
    "rounding": "not-applied",
    "amountEncoding": "canonical-decimal-string",
    "displayExample": "500 000,00 KZT",
    "calculationNote": "Доходы, расходы и бюджет относятся к month; цели и долги — к snapshotDate. Остаток бюджета не является балансом счёта или доступными деньгами."
  },
  "accounts": [
    {
      "id": "account-main",
      "revision": 1,
      "name": "Основной",
      "currency": "KZT",
      "scale": 2,
      "archived": false,
      "opening": null,
      "balanceState": "unknown",
      "displayNote": "Начальный баланс не задан"
    }
  ],
  "categories": [
    {
      "id": "category-salary",
      "revision": 1,
      "name": "Зарплата",
      "kind": "income",
      "archived": false
    },
    {
      "id": "category-housing",
      "revision": 1,
      "name": "Жильё",
      "kind": "expense",
      "archived": false
    },
    {
      "id": "category-groceries",
      "revision": 1,
      "name": "Продукты",
      "kind": "expense",
      "archived": false
    },
    {
      "id": "category-transport",
      "revision": 1,
      "name": "Транспорт",
      "kind": "expense",
      "archived": false
    }
  ],
  "operations": [
    {
      "id": "operation-salary-2026-10",
      "revision": 1,
      "type": "income",
      "date": "2026-10-25",
      "accountId": "account-main",
      "categoryId": "category-salary",
      "amount": { "currency": "KZT", "scale": 2, "amount": "500000.00" }
    },
    {
      "id": "operation-housing-2026-10",
      "revision": 1,
      "type": "expense",
      "date": "2026-10-01",
      "accountId": "account-main",
      "categoryId": "category-housing",
      "amount": { "currency": "KZT", "scale": 2, "amount": "150000.00" }
    },
    {
      "id": "operation-groceries-2026-10",
      "revision": 1,
      "type": "expense",
      "date": "2026-10-12",
      "accountId": "account-main",
      "categoryId": "category-groceries",
      "amount": { "currency": "KZT", "scale": 2, "amount": "120000.00" }
    },
    {
      "id": "operation-transport-2026-10",
      "revision": 1,
      "type": "expense",
      "date": "2026-10-20",
      "accountId": "account-main",
      "categoryId": "category-transport",
      "amount": { "currency": "KZT", "scale": 2, "amount": "30000.00" }
    }
  ],
  "budget": {
    "id": "budget-2026-10-kzt",
    "revision": 1,
    "month": "2026-10",
    "currency": "KZT",
    "scale": 2,
    "mode": "category",
    "plans": [
      {
        "categoryId": "category-housing",
        "planned": { "currency": "KZT", "scale": 2, "amount": "150000.00" }
      },
      {
        "categoryId": "category-groceries",
        "planned": { "currency": "KZT", "scale": 2, "amount": "150000.00" }
      },
      {
        "categoryId": "category-transport",
        "planned": { "currency": "KZT", "scale": 2, "amount": "50000.00" }
      }
    ]
  },
  "goals": [
    {
      "id": "goal-emergency-fund",
      "revision": 1,
      "name": "Подушка безопасности",
      "snapshotDate": "2026-10-30",
      "target": { "currency": "KZT", "scale": 2, "amount": "600000.00" },
      "accumulated": { "currency": "KZT", "scale": 2, "amount": "180000.00" },
      "accumulatedBeforeMonth": true,
      "contributionsInMonth": [],
      "ledgerEffect": "none"
    }
  ],
  "debts": [
    {
      "id": "debt-example-a",
      "revision": 1,
      "direction": "owe",
      "label": "Пример А",
      "snapshotDate": "2026-10-30",
      "outstanding": { "currency": "KZT", "scale": 2, "amount": "80000.00" },
      "dueDate": "2026-11-15",
      "recordedBeforeMonth": true,
      "repaymentsInMonth": [],
      "historyState": "not-provided",
      "ledgerEffect": "none"
    },
    {
      "id": "debt-example-b",
      "revision": 1,
      "direction": "owedToMe",
      "label": "Пример Б",
      "snapshotDate": "2026-10-30",
      "outstanding": { "currency": "KZT", "scale": 2, "amount": "50000.00" },
      "dueDate": "2026-11-20",
      "recordedBeforeMonth": true,
      "repaymentsInMonth": [],
      "historyState": "not-provided",
      "ledgerEffect": "none"
    }
  ],
  "expected": {
    "purpose": "Точные ожидаемые значения D1 для макетов и независимой проверки; не баланс счёта.",
    "monthly": {
      "month": "2026-10",
      "income": { "currency": "KZT", "scale": 2, "amount": "500000.00" },
      "expense": { "currency": "KZT", "scale": 2, "amount": "300000.00" },
      "budgetPlanned": { "currency": "KZT", "scale": 2, "amount": "350000.00" },
      "budgetSpent": { "currency": "KZT", "scale": 2, "amount": "300000.00" },
      "budgetRemaining": { "currency": "KZT", "scale": 2, "amount": "50000.00" },
      "categories": [
        {
          "categoryId": "category-housing",
          "planned": { "currency": "KZT", "scale": 2, "amount": "150000.00" },
          "spent": { "currency": "KZT", "scale": 2, "amount": "150000.00" },
          "remaining": { "currency": "KZT", "scale": 2, "amount": "0.00" }
        },
        {
          "categoryId": "category-groceries",
          "planned": { "currency": "KZT", "scale": 2, "amount": "150000.00" },
          "spent": { "currency": "KZT", "scale": 2, "amount": "120000.00" },
          "remaining": { "currency": "KZT", "scale": 2, "amount": "30000.00" }
        },
        {
          "categoryId": "category-transport",
          "planned": { "currency": "KZT", "scale": 2, "amount": "50000.00" },
          "spent": { "currency": "KZT", "scale": 2, "amount": "30000.00" },
          "remaining": { "currency": "KZT", "scale": 2, "amount": "20000.00" }
        }
      ]
    },
    "snapshot": {
      "snapshotDate": "2026-10-30",
      "goalProgress": [
        {
          "goalId": "goal-emergency-fund",
          "percent": "30",
          "rounding": "not-applied",
          "calculation": "180000.00 / 600000.00 * 100 = 30%"
        }
      ],
      "debtTotalsByDirection": {
        "owe": { "currency": "KZT", "scale": 2, "amount": "80000.00" },
        "owedToMe": { "currency": "KZT", "scale": 2, "amount": "50000.00" }
      }
    }
  }
};
