# Проверка финансового отдела — APP-001-FIN v1

Дата: 30 октября 2026. Ответственный и проверяющий: `/root/personal_finance_model`, finance-lead.
Родительская задача: APP-001, структура и демонстрационный дизайн личных финансов.
Объект проверки — документы и синтетические числа D1; работающего приложения здесь нет.

## Исполнители и владение

- APP-001-FIN-MODEL: `/root/personal_finance_model/formulas`, calculation-analyst, единственный автор `docs/finance/MODEL.md`.
- APP-001-FIN-D1: `/root/personal_finance_model/fixture`, financial-reference-specialist, единственный автор `design/demo-data.json`.
- Глава прочитал оба результата и проверяет их отдельно от авторов. Этот отчёт принадлежит главе; другие файлы глава не редактирует.
- Специалистам переданы полные профили, AGENTS.md/PROTOCOL, продуктовые и архитектурные входы. Финальные решения пользователя по Q-02–04 не подменяются предложениями.

## Зафиксированная версия

- `docs/finance/MODEL.md`: SHA-256 `f020cb4dd08d8801a03ee5c7203edc76740f91cea77ce49b3f1719a42ca98674`.
- `design/demo-data.json`: SHA-256 `8c6bb181ed342867e453640c1f3f61384dc2c4425a1789d813d01236a79e3c81`.
- Прочитанные входы: `docs/product/CONCEPT.md` v0.1, `docs/product/SCREENS.md` v1, `docs/architecture/STRUCTURE.md` v1.
- Их SHA-256 соответственно: `9be797db0efe9b3fee0c3ee761128f3f76dc6af4ab57166f85b58954e9f5c540`, `f2217b2d387a9ff2ec5c22e50f46d6ed3720098e7b02cb21438d21e80df99503`, `9bee45e95c6a3b7307521768934293cc2b23d2a80c091d5d0d330b65b6edca60`.

## Проверка главы

- Прочитаны деньги, календарные периоды, бюджет, перевод, цель и долг; сверены с архитектурными инвариантами и D1 SCREENS.
- FIN-R01, P2, исправлено автором: число знаков MoneyDTO определяется scale; два знака явно относятся к D1. Глава повторно прочитал исправленную строку; версия выше включает исправление.
- Денежные суммы точны в минимальных единицах; фильтры списка не меняют бюджет, расходы категорий не складываются повторно с общим итогом.
- Ручные цели/долги не создают ledger-операции; D1 не притворяется runtime backup. История долгов неизвестна, исходные principal и прошлые даты не выдуманы.
- Даты операций относятся к месяцу; цели/долги — к snapshotDate. Начальный баланс неизвестен, разность доходов и расходов не названа балансом.
- Проверка кода ниже: ожидаются 27 канонических MoneyDTO, 4 операции, доход 50000000, расход 30000000, план 35000000, остаток 5000000 минимальных единиц; цель ровно 30%, долги 8000000/5000000 отдельно.
- Статус запуска кода и фактический вывод записаны ниже после выполнения. Это проверка фикстуры, математики и инструмента сверки, а не тестирование приложения.

## Воспроизведение

Из корня проекта, PowerShell с Python 3.13.3:

```powershell
$reviewText = Get-Content -LiteralPath 'docs/finance/HEAD_REVIEW.md' -Raw
$reviewCode = [regex]::Match($reviewText, '(?s)```python\r?\n(.*?)\r?\n```').Groups[1].Value
$reviewCode | python -
```

Полный исполненный код; эталоны заданы из SCREENS, expected внутри JSON не служит единственным источником ожиданий:

```python
import hashlib, json, re
from pathlib import Path
from datetime import date
from decimal import Decimal, ROUND_HALF_UP

hashes = {
    'docs/finance/MODEL.md': 'f020cb4dd08d8801a03ee5c7203edc76740f91cea77ce49b3f1719a42ca98674',
    'design/demo-data.json': '8c6bb181ed342867e453640c1f3f61384dc2c4425a1789d813d01236a79e3c81',
}
for path, expected_hash in hashes.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected_hash, path
model = Path('docs/finance/MODEL.md').read_text(encoding='utf-8')
assert len(model.splitlines()) <= 100
d = json.loads(Path('design/demo-data.json').read_text(encoding='utf-8'))

def amount(s):
    assert isinstance(s, str) and re.fullmatch(r'-?(0|[1-9][0-9]*)\.[0-9]{2}', s), s
    assert s != '-0.00'
    sign = -1 if s.startswith('-') else 1
    whole, fraction = s.lstrip('-').split('.')
    return sign * (int(whole) * 100 + int(fraction))

def money(m):
    assert m['currency'] == 'KZT' and type(m['scale']) is int and m['scale'] == 2
    return amount(m['amount'])

money_count = 0
def walk(v):
    global money_count
    if isinstance(v, dict):
        assert not {'balance', 'available', 'availableToSpend'} & v.keys()
        if {'currency', 'scale', 'amount'} <= v.keys():
            money(v)
            money_count += 1
        for child in v.values(): walk(child)
    elif isinstance(v, list):
        for child in v: walk(child)
walk(d)
assert money_count == 27
assert amount('0.00') == 0 and amount('-0.01') == -1
assert amount('99999999999999999999.99') == 9999999999999999999999
for bad in ['', '-0.00', '+1.00', '01.00', '1.000', '1', 'NaN', 'Infinity', '1e2', '1,00']:
    try: amount(bad)
    except AssertionError: pass
    else: raise AssertionError(bad)

m = d['metadata']
assert (d['fixtureId'], m['month'], m['snapshotDate'], m['currency'], m['scale']) == ('D1','2026-10','2026-10-30','KZT',2)
assert m['isRuntimeBackup'] is False and m['rounding'] == 'not-applied'
start, end = date(2026,10,1), date(2026,11,1)
assert m['period'] == {'startInclusive':start.isoformat(), 'endExclusive':end.isoformat()}
assert start <= date(2026,10,31) < end and not (start <= end < end)
assert date.fromisoformat('2028-02-29').day == 29
try: date.fromisoformat('2026-02-29')
except ValueError: pass
else: raise AssertionError('invalid calendar date accepted')
accounts = {a['id']:a for a in d['accounts']}
cats = {c['id']:c for c in d['categories']}
assert len(accounts) == len(d['accounts']) == 1
assert len(cats) == len(d['categories']) == 4
account = next(iter(accounts.values()))
assert account['opening'] is None and account['balanceState'] == 'unknown'
ops = d['operations']
assert len(ops) == len({o['id'] for o in ops}) == 4
fixed_ops = {
    ('income','2026-10-25','category-salary',50000000),
    ('expense','2026-10-01','category-housing',15000000),
    ('expense','2026-10-12','category-groceries',12000000),
    ('expense','2026-10-20','category-transport',3000000),
}
assert {(o['type'],o['date'],o['categoryId'],money(o['amount'])) for o in ops} == fixed_ops
for o in ops:
    assert start <= date.fromisoformat(o['date']) < end
    assert o['accountId'] in accounts and cats[o['categoryId']]['kind'] == o['type']
    assert money(o['amount']) > 0
income = sum(money(o['amount']) for o in ops if o['type'] == 'income')
spent = sum(money(o['amount']) for o in ops if o['type'] == 'expense')
b = d['budget']
assert b['mode'] == 'category' and b['month'] == m['month'] and b['currency'] == 'KZT' and b['scale'] == 2
plans = {p['categoryId']:money(p['planned']) for p in b['plans']}
assert len(plans) == len(b['plans']) == 3
fixed_categories = {'category-housing':(15000000,15000000,0), 'category-groceries':(15000000,12000000,3000000), 'category-transport':(5000000,3000000,2000000)}
assert plans == {k:v[0] for k,v in fixed_categories.items()}
planned = sum(plans.values())
assert (income,spent,planned,planned-spent) == (50000000,30000000,35000000,5000000)
e = d['expected']['monthly']
assert e['month'] == m['month']
for key, value in {'income':income,'expense':spent,'budgetPlanned':planned,'budgetSpent':spent,'budgetRemaining':planned-spent}.items():
    assert money(e[key]) == value
assert len(e['categories']) == 3
for c in e['categories']:
    category = c['categoryId']
    actual = sum(money(o['amount']) for o in ops if o['type']=='expense' and o['categoryId']==category)
    triple = (plans[category], actual, plans[category]-actual)
    assert triple == fixed_categories[category]
    assert tuple(money(c[k]) for k in ('planned','spent','remaining')) == triple
assert sum(money(c['remaining']) for c in e['categories']) == planned-spent
g, = d['goals']
assert (money(g['accumulated']), money(g['target'])) == (18000000,60000000)
assert g['snapshotDate']==m['snapshotDate'] and g['accumulatedBeforeMonth'] and not g['contributionsInMonth'] and g['ledgerEffect']=='none'
assert money(g['accumulated'])*100 == money(g['target'])*30
snapshot = d['expected']['snapshot']
assert snapshot['snapshotDate'] == m['snapshotDate']
assert snapshot['goalProgress'][0]['goalId']==g['id'] and snapshot['goalProgress'][0]['percent']=='30' and snapshot['goalProgress'][0]['rounding']=='not-applied'
debts = d['debts']
assert len(debts)==2 and {x['direction']:money(x['outstanding']) for x in debts}=={'owe':8000000,'owedToMe':5000000}
for debt in debts:
    assert debt['snapshotDate']==m['snapshotDate'] and debt['recordedBeforeMonth'] and not debt['repaymentsInMonth'] and debt['ledgerEffect']=='none'
    assert debt['historyState']=='not-provided' and 'principal' not in debt
    assert debt['dueDate']=={'owe':'2026-11-15','owedToMe':'2026-11-20'}[debt['direction']]
    date.fromisoformat(debt['dueDate'])
    assert money(snapshot['debtTotalsByDirection'][debt['direction']])==money(debt['outstanding'])
# Independent Decimal rounding checks the candidate integer formula, not app code.
for a,t,want in [(0,200,0),(1,200,1),(1,201,0),(199,200,100),(200,200,100),(201,200,101),(18000000,60000000,30)]:
    exact = (Decimal(100)*a/t).quantize(Decimal('1'), rounding=ROUND_HALF_UP)
    candidate = (200*a+t)//(2*t)
    assert candidate == int(exact) == want
print('PASS: fixed hashes; MODEL 68/100 lines; MoneyDTO=27; operations=4')
print(f'PASS: income={income}; expense={spent}; budget={planned}; remaining={planned-spent}; goal=30%; debts=8000000/5000000')
print('PASS: exact parser examples; calendar boundaries; candidate HALF_UP boundaries; no application tested')
```

## Фактический результат

Команда выше выполнена главой через `exec_command`, Python 3.13.3, exit code 0. Ожидание совпало с фактом:

```text
PASS: fixed hashes; MODEL 68/100 lines; MoneyDTO=27; operations=4
PASS: income=50000000; expense=30000000; budget=35000000; remaining=5000000; goal=30%; debts=8000000/5000000
PASS: exact parser examples; calendar boundaries; candidate HALF_UP boundaries; no application tested
```

Отдельно выполнены `Select-String -LiteralPath 'docs/finance/MODEL.md' -Pattern 'DTO/хранение'` и `Get-FileHash -LiteralPath 'docs/finance/MODEL.md','design/demo-data.json' -Algorithm SHA256`: уточнение scale найдено, хэши совпали с зафиксированной версией. Заключение главы: числовая D1 и кандидатная спецификация согласованы; FIN-R01 закрыто. Разрешена передача материалов для дизайна и независимой приёмки. Принятие всей задачи остаётся за начальником после QA и суда.

## Передача дизайнеру и QA

- Использовать одну D1 на всех экранах: месяц «Октябрь 2026», KZT, денежные суммы с двумя знаками и пометка «Демо».
- Карточки: «Доходы месяца» 500 000,00; «Расходы месяца» 300 000,00; «План бюджета» 350 000,00; «Потрачено» 300 000,00; «Остаток бюджета» 50 000,00 KZT.
- Категории план/факт: жильё 150 000,00/150 000,00, продукты 150 000,00/120 000,00, транспорт 50 000,00/30 000,00 KZT. Это разрез тех же расходов.
- Цель 180 000,00/600 000,00 KZT, 30% точно; долги «Я должен» 80 000,00 и «Мне должны» 50 000,00 KZT; состояние на 30.10.2026.
- Счёт: «Основной · KZT · начальный баланс не задан». Не выводить баланс, доступные деньги или 200 000,00 как остаток счёта. Не вычитать цели/долги из бюджета.
- Процент бюджета не нужен: числа план/факт/остаток дают точный смысл без округления. Все признаки направления дополнять словами, цвет не единственный носитель смысла.
- Варианты ошибки: «Введите сумму больше 0», «Не более 2 знаков после запятой»; в бюджете нулевой лимит допустим, пустой ввод не превращается в ноль. Правила будущей реализации остаются кандидатными.

## Открытые решения и ограничения

- FIN-Q01, P2, открытое решение Q-02: верхние суммы/длина, диапазон и будущие даты, валюты, точность и часовой пояс до реализации.
- FIN-Q02, P2, ограничение данных: D1 содержит только остатки долгов. Восстановление principal/истории нельзя проверить или изображать известным.
- FIN-Q03, P2, открытое решение Q-03/Q-04: общий/категорийный бюджет, превышение цели, переплата долга, связь со счетами и дробные проценты. HALF_UP — математически проверенный кандидат, не пользовательское решение.
- Текущие ставки, налоги, валютные курсы и законодательные правила задаче не нужны и не использовались. Внешние источники для синтетической арифметики не требовались.
- Приложение, формы, сохранение, импорт и браузерные сценарии не реализованы и не проверены. Проверка главы не заменяет независимый QA и суд.
- Следующее действие: передать неизменённую версию креативному отделу, затем независимому QA и суду вместе с этим кодом и фактическим выводом.
