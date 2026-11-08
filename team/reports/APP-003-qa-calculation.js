'use strict';
// Independent runtime QA: run `node team/reports/APP-003-qa-calculation.js`.
// Exact expectations originate in APP-003, MODEL and SCREENS D1, not the
// frontend author's test or fixture.expected. No browser, DOM or source writes.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const manifestPath = 'team/snapshots/local-prototype-v1.json';
const expectedDigest = 'be7ef8f3e912744b04804a7a225fdee44ad6917254de68529f97a2af728e354e';
const read = name => fs.readFileSync(path.join(root, name));
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const json = bytes => JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/, ''));
const plain = value => value && typeof value === 'object' ? JSON.parse(JSON.stringify(value)) : value;
const cash = amount => ({ currency: 'KZT', scale: 2, amount });
let assertions = 0, passed = 0;
const failures = [];
function eq(actual, expected, label) {
  assert.deepEqual(plain(actual), plain(expected), label); assertions++;
}
function yes(value, label) { assert.ok(value, label); assertions++; }
function rejects(fn, label) { assert.throws(fn, undefined, label); assertions++; }
function test(id, label, fn) {
  try { fn(); passed++; console.log(`PASS ${id}: ${label}`); }
  catch (error) {
    failures.push({ id, label, message: error.message });
    console.log(`FAIL ${id}: ${label}\n${error.stack}`);
  }
}

const manifestBytes = read(manifestPath);
const manifest = json(manifestBytes);
const raw = new Map();
test('CAL-01', '46 frozen hashes, ordinal digest and all 26 historical hashes', () => {
  eq(manifest.version, 'local-prototype-v1'); eq(manifest.files.length, 46);
  eq(new Set(manifest.files.map(entry => entry.path)).size, 46);
  for (const entry of manifest.files) {
    yes(!entry.path.startsWith('/') && !entry.path.includes('\\') && !entry.path.split('/').includes('..'), entry.path);
    const bytes = read(entry.path); raw.set(entry.path, bytes);
    eq(hash(bytes), entry.sha256, entry.path);
  }
  const entries = [...manifest.files].sort((a,b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
  const digest = hash(Buffer.from(entries.map(entry => `${hash(raw.get(entry.path))}  ${entry.path}\n`).join(''), 'utf8'));
  eq(digest, expectedDigest); eq(manifest.manifestSha256, expectedDigest);
  const historic = json(read('team/snapshots/app-concept-v1-partial.json'));
  eq(historic.files.length, 26);
  for (const old of historic.files) {
    eq(hash(read(old.path)), old.sha256, 'historical ' + old.path);
    eq(manifest.files.find(entry => entry.path === old.path)?.sha256, old.sha256);
  }
});
if (failures.length) { console.error('Cannot test the named runtime version with mismatching hashes.'); process.exit(1); }
const fixture = json(raw.get('design/demo-data.json'));
function runtime() {
  const context = vm.createContext({}); // no document, window, network or DOM stubs
  for (const name of ['demo-data.js', 'model.js', 'app.js']) {
    new vm.Script(raw.get('prototype/' + name).toString('utf8'), { filename: name })
      .runInContext(context, { timeout: 2000 });
  }
  return { context, m: context.LadnoModel, a: context.LadnoApp };
}
const fields = { type: 'expense', amount: '1200,99', date: '2026-10-12', accountId: 'account-main', categoryId: 'category-groceries', note: 'QA synthetic' };
const totals = (m, data, month = '2026-10') => {
  const s = m.summary(data, month);
  return { income: s.income, expense: s.expense, planned: s.planned, remaining: s.remaining };
};
const initialTotals = { income: '500000.00', expense: '300000.00', planned: '350000.00', remaining: '50000.00' };
function frozenLedger(m, a) { return JSON.stringify({ operations: a.state.data.operations, totals: totals(m, a.state.data), accounts: a.state.data.accounts }); }

test('CAL-02', 'fresh VM globals, deep-equal source D1, initial independent financial oracle', () => {
  const { context, m, a } = runtime();
  eq(plain(context.LadnoDemoData), fixture); eq(a.state.data, fixture);
  eq(a.state.data.operations.length, 4); eq(totals(m, a.state.data), initialTotals);
  eq(m.summary(a.state.data, '2026-10').categories.map(item => [item.id, item.remaining]), [
    ['category-housing', '0.00'], ['category-groceries', '30000.00'], ['category-transport', '20000.00']
  ]);
  eq(m.goalProgress(a.state.data.goals[0]), { percent: '30', barPercent: '30', reached: false });
  eq(m.debtTotal(a.state.data, 'owe'), '80000.00'); eq(m.debtTotal(a.state.data, 'owedToMe'), '50000.00');
  eq(a.state.data.accounts[0].opening, null); eq(a.state.data.accounts[0].balanceState, 'unknown');
  eq(a.state.month, '2026-10'); eq(a.state.screenState, 'ready');
});
test('CAL-03', 'canonical money and BigInt round-trip, including exact values above 2^53', () => {
  const { m } = runtime();
  for (const [value, minor] of [['0.00',0n], ['0.01',1n], ['-0.01',-1n], ['1200.99',120099n], ['9007199254740993.01',900719925474099301n]]) {
    eq(m.toMinor(value), minor); eq(typeof m.toMinor(value), 'bigint'); eq(m.fromMinor(minor), value);
  }
  eq(m.fromMinor(900719925474099301n + 1n), '9007199254740993.02');
  eq(m.formatMoney('9007199254740993.01'), '9 007 199 254 740 993,01 KZT');
  eq(m.formatMoney('-0.01'), '−0,01 KZT'); eq(m.fromMinor(-0n), '0.00');
  for (const bad of ['-0.00', '-00.00', '01.00', '+1.00', '1', '1.0', '1.000', '1,00', '1e2', 'NaN', 'Infinity', ' 1.00', 1, null]) rejects(() => m.toMinor(bad), String(bad));
  rejects(() => m.fromMinor(1), 'Number is not a minor-unit bigint');
});
test('CAL-04', 'raw input normalization, precision rejection and zero/negative rules', () => {
  const { m } = runtime();
  for (const [input, expected] of [['1','1.00'], ['1,2','1.20'], ['1.20','1.20'], ['00012,01','12.01'], ['0,01','0.01']]) {
    eq(m.parseAmount(input, false).ok, true); eq(m.parseAmount(input, false).amount, expected);
  }
  for (const bad of ['', '0', '0.00', '-0', '-0.00', '-1', '1200,999', '1.000', '1e3', 'NaN', 'Infinity', '1 200,00', '+1', ' 1', '.5', '1,']) eq(m.parseAmount(bad, false).ok, false, bad);
  eq(m.parseAmount('0', true).amount, '0.00'); eq(m.parseAmount('0.00', true).minor, 0n);
  eq(m.parseAmount('-0.00', true).ok, false); eq(m.parseAmount('1200,999', false).error, 'Не более 2 знаков после запятой');
});
test('CAL-05', 'real calendar dates and valid month boundaries', () => {
  const { m } = runtime();
  for (const valid of ['2026-10-01','2026-10-31','2026-11-01','2024-02-29','2000-02-29']) eq(m.validDate(valid), true, valid);
  for (const bad of ['2026-02-29','1900-02-29','2026-04-31','2026-13-01','2026-10-00','0000-01-01','2026-1-01','2026-10-01T00:00:00Z','',null]) eq(m.validDate(bad), false, String(bad));
  for (const [month, want] of [['2026-10',true],['2026-01',true],['2026-12',true],['2026-00',false],['2026-13',false],['0000-01',false],['2026-1',false]]) eq(m.validMonth(month), want);
});
test('CAL-06', 'invalid controller submit preserves all raw values with no extra operation', () => {
  const { m, a } = runtime(); a.dispatch('open-operation');
  const rawValues = { ...fields, amount: '1200,999', note: 'Синтетический ввод <qa>' };
  const before = frozenLedger(m,a); const result = a.submit(rawValues);
  eq(result.ok, false); eq(a.state.form.values, rawValues); eq(a.state.form.errors.amount, 'Не более 2 знаков после запятой');
  eq(a.state.data.operations.length, 4); eq(frozenLedger(m,a), before);
  eq(a.state.form.values.amount, '1200,999');
});
test('CAL-07', 'valid expense changes correct October totals exactly once', () => {
  const { m, a } = runtime(); a.dispatch('open-operation');
  eq(a.submit(fields).ok, true); eq(a.state.data.operations.length, 5);
  eq(totals(m, a.state.data), { income:'500000.00', expense:'301200.99', planned:'350000.00', remaining:'48799.01' });
  eq(m.summary(a.state.data, '2026-10').categories.find(item => item.id === 'category-groceries').remaining, '28799.01');
  eq(a.state.data.operations.at(-1).amount, cash('1200.99')); eq(a.state.form, null);
  eq(a.submit(fields).ok, false); eq(a.state.data.operations.length, 5);
});
test('CAL-08', 'income remains outside expense budget and huge value remains exact', () => {
  const { m, a } = runtime(); a.dispatch('open-operation');
  eq(a.submit({ ...fields, type:'income', categoryId:'category-salary', amount:'0,01' }).ok, true);
  eq(totals(m,a.state.data), { ...initialTotals, income:'500000.01' });
  a.dispatch('reset-demo'); a.dispatch('open-operation');
  eq(a.submit({ ...fields, type:'income', categoryId:'category-salary', amount:'9007199254740993,01' }).ok, true);
  eq(totals(m,a.state.data), { ...initialTotals, income:'9007199255240993.01' });
  eq(a.state.data.operations.at(-1).amount.amount, '9007199254740993.01');
});
test('CAL-09', 'invalid amounts/dates/references reject without altering data', () => {
  const { m, a } = runtime();
  const invalidValues = [
    ...['','0','-0.00','-0.01','1.000'].map(amount => ({...fields,amount})),
    {...fields,date:'2026-02-29'}, {...fields,date:'2026-04-31'}, {...fields,date:''},
    {...fields,accountId:'missing'}, {...fields,categoryId:'category-salary'}, {...fields,categoryId:''}, {...fields,type:'missing'}
  ];
  for (const values of invalidValues) {
    a.dispatch('open-operation'); const before = frozenLedger(m,a);
    eq(a.submit(values).ok, false); eq(frozenLedger(m,a), before); eq(a.state.form.values, values);
  }
  const wrongCurrency = plain(fixture); wrongCurrency.accounts[0].currency='USD';
  eq(m.applyForm(wrongCurrency,'operation',fields).ok, false);
  const wrongScale = plain(fixture); wrongScale.accounts[0].scale=3;
  eq(m.applyForm(wrongScale,'operation',fields).ok, false);
});
test('CAL-10', 'month inclusion is date-only at Sep/Oct/Nov boundaries', () => {
  const { m, a } = runtime();
  for (const date of ['2026-09-30','2026-10-01','2026-10-31','2026-11-01']) {
    a.dispatch('open-operation'); eq(a.submit({...fields,amount:'0.01',date}).ok, true);
  }
  eq(a.state.month, '2026-11');
  eq(totals(m,a.state.data,'2026-10'), {...initialTotals,expense:'300000.02',remaining:'49999.98'});
  eq(totals(m,a.state.data,'2026-09'), {income:'0.00',expense:'0.01',planned:null,remaining:null});
  eq(totals(m,a.state.data,'2026-11'), {income:'0.00',expense:'0.01',planned:null,remaining:null});
  eq(m.operationsFor(a.state.data,'2026-10','all').length,6);
  a.dispatch('change-month','2026-13'); eq(a.state.month,'2026-11');
  a.dispatch('change-month','2026-10'); eq(a.state.month,'2026-10');
});
test('CAL-11', 'no budget differs from explicit zero and unplanned expense is included', () => {
  const { m } = runtime(); const missing = plain(fixture); missing.budget=null;
  eq(totals(m,missing), {...initialTotals,planned:null,remaining:null});
  eq(m.summary(missing,'2026-10').status,'unset');
  const zero = plain(fixture); zero.budget={month:'2026-10',currency:'KZT',scale:2,mode:'total',planned:cash('0.00')};
  eq(totals(m,zero), {...initialTotals,planned:'0.00',remaining:'-300000.00'}); eq(m.summary(zero,'2026-10').status,'over');
  const unplanned = plain(fixture); unplanned.budget.plans=unplanned.budget.plans.filter(row=>row.categoryId!=='category-groceries');
  eq(totals(m,unplanned), {...initialTotals,planned:'200000.00',remaining:'-100000.00'});
  const grocery = m.summary(unplanned,'2026-10').categories.find(row=>row.id==='category-groceries');
  eq([grocery.spent,grocery.planned,grocery.remaining],['120000.00',null,null]);
  const duplicate = plain(fixture); duplicate.budget.plans.push(plain(duplicate.budget.plans[0]));
  rejects(()=>m.summary(duplicate,'2026-10'),'duplicate category');
});
test('CAL-12', 'different currencies/scales cannot enter monetary summary', () => {
  const { m } = runtime();
  for (const [location, field, value] of [['operation','currency','USD'],['operation','scale',3],['budget','currency','USD']]) {
    const d=plain(fixture); const target=location==='operation'?d.operations[0].amount:d.budget; target[field]=value;
    rejects(()=>m.summary(d,'2026-10'),`${location} ${field}`);
  }
});
test('CAL-13', 'single-account transfer, including same from/to, is blocked without invented account', () => {
  const { m, a } = runtime();
  for (const extra of [{}, {fromAccountId:'account-main',toAccountId:'account-main'}]) {
    a.dispatch('open-operation'); const before=frozenLedger(m,a);
    eq(a.submit({...fields,type:'transfer',amount:'0.01',...extra}).ok,false);
    yes(a.state.form.errors.type.includes('два разных счёта')); eq(a.state.data.accounts.length,1); eq(frozenLedger(m,a),before);
  }
});
test('CAL-14', 'goal create/edit/zero/exceed target preserves ledger and independent account state', () => {
  const { m, a } = runtime(); const before=frozenLedger(m,a);
  a.dispatch('open-goal'); eq(a.submit({name:'QA цель',target:'100.00',accumulated:'0'}).ok,true);
  eq(a.state.data.goals.length,2); eq(frozenLedger(m,a),before);
  const added=a.state.data.goals.at(-1); eq(added.accumulated.amount,'0.00'); eq(added.ledgerEffect,'none');
  a.dispatch('edit-goal',undefined,'goal-emergency-fund'); eq(a.submit({accumulated:'600000.01'}).ok,true);
  eq(a.state.data.goals[0].accumulated.amount,'600000.01'); eq(m.goalProgress(a.state.data.goals[0]).reached,true); eq(frozenLedger(m,a),before);
  a.dispatch('edit-goal',undefined,'goal-emergency-fund'); eq(a.submit({accumulated:'-0.01'}).ok,false); eq(frozenLedger(m,a),before);
  a.dispatch('open-goal'); eq(a.submit({name:'QA',target:'0',accumulated:'0'}).ok,false); eq(a.state.data.goals.length,2);
});
test('CAL-15', 'goal HALF_UP percentage does not determine reached threshold', () => {
  const { m }=runtime();
  const goal={target:cash('2.00'),accumulated:cash('1.99')};
  eq(m.goalProgress(goal),{percent:'100',barPercent:'100',reached:false});
  goal.accumulated=cash('2.00'); eq(m.goalProgress(goal),{percent:'100',barPercent:'100',reached:true});
  goal.accumulated=cash('2.02'); eq(m.goalProgress(goal),{percent:'101',barPercent:'100',reached:true});
});
test('CAL-16', 'new debts stay separate and reject invalid label/direction/date/amount', () => {
  const { m,a }=runtime(); const before=frozenLedger(m,a);
  const values={direction:'owedToMe',label:'QA долг',amount:'15.55',dueDate:'2026-11-15'};
  a.dispatch('open-debt'); eq(a.submit(values).ok,true); eq(m.debtTotal(a.state.data,'owedToMe'),'50015.55'); eq(m.debtTotal(a.state.data,'owe'),'80000.00'); eq(frozenLedger(m,a),before);
  for (const invalid of [{...values,label:' '},{...values,direction:'bad'},{...values,amount:'0'},{...values,dueDate:'2026-02-29'}]) {
    a.dispatch('open-debt'); eq(a.submit(invalid).ok,false); eq(a.state.data.debts.length,3); eq(frozenLedger(m,a),before);
  }
});
test('CAL-17', 'repayment overpayment/date errors and exact full repayment have no ledger effect', () => {
  const {m,a}=runtime(); const before=frozenLedger(m,a);
  for(const values of [{amount:'80000.01',date:'2026-10-30'},{amount:'0',date:'2026-10-30'},{amount:'1',date:'2026-02-29'},{amount:'1',date:'2026-10-31'}]) {
    a.dispatch('repay-debt',undefined,'debt-example-a'); eq(a.submit(values).ok,false); eq(a.state.form.values,values); eq(m.debtTotal(a.state.data,'owe'),'80000.00'); eq(frozenLedger(m,a),before);
  }
  a.dispatch('repay-debt',undefined,'debt-example-a'); eq(a.submit({amount:'0.01',date:'2026-10-30'}).ok,true);
  eq(m.debtTotal(a.state.data,'owe'),'79999.99'); eq(m.debtTotal(a.state.data,'owedToMe'),'50000.00'); eq(frozenLedger(m,a),before);
  a.dispatch('repay-debt',undefined,'debt-example-a'); eq(a.submit({amount:'79999.99',date:'2026-10-30'}).ok,true);
  eq(m.debtTotal(a.state.data,'owe'),'0.00'); eq(frozenLedger(m,a),before);
  a.dispatch('repay-debt',undefined,'debt-example-a'); eq(a.submit({amount:'0.01',date:'2026-10-30'}).ok,false);
});
test('CAL-18', 'controller tabs, filters and demo states alter selection, not underlying money', () => {
  const {m,a}=runtime(); const before=JSON.stringify(a.state.data);
  for(const section of ['operations','goals','debts','overview']) { a.dispatch('navigate',section); eq(a.state.section,section); }
  for(const [filter,count] of [['income',1],['expense',3],['transfer',0]]) {
    a.dispatch('set-filter',filter); eq(a.state.filter,filter); eq(m.operationsFor(a.state.data,'2026-10',filter).length,count); eq(totals(m,a.state.data),initialTotals);
  }
  a.dispatch('reset-filter'); eq(a.state.filter,'all'); eq(m.operationsFor(a.state.data,'2026-10','all').length,4);
  for(const state of ['empty','loading','error']) { a.dispatch('screen-state',state); eq(a.state.screenState,state); eq(JSON.stringify(a.state.data),before); }
  a.dispatch('retry'); eq(a.state.screenState,'ready'); eq(JSON.stringify(a.state.data),before);
});
test('CAL-19', 'reset/fresh execution restore D1; caller cannot mutate controller through state getter', () => {
  const {context,m,a}=runtime();
  const detached=a.state; detached.data.operations=[]; eq(a.state.data.operations.length,4);
  a.dispatch('open-operation'); eq(a.submit(fields).ok,true);
  a.dispatch('screen-state','error'); a.dispatch('navigate','debts'); a.dispatch('set-debt-direction','owedToMe');
  a.dispatch('reset-demo'); eq(a.state.data,fixture); eq(a.state.section,'overview'); eq(a.state.month,'2026-10'); eq(a.state.screenState,'ready'); eq(a.state.form,null); eq(a.state.filter,'all'); eq(a.state.debtDirection,'owe');
  eq(context.LadnoDemoData,fixture); eq(totals(m,a.state.data),initialTotals);
  const another=runtime(); eq(another.a.state.data,fixture); eq(another.a.state.data.operations.length,4);
});
test('CAL-20', 'static package references are local; network/storage/float APIs are absent (not an offline-browser test)', () => {
  const html=raw.get('prototype/index.html').toString('utf8');
  const resources=[...html.matchAll(/(?:src|href)="([^"]+)"/g)].map(match=>match[1]);
  eq(resources,['styles.css','demo-data.js','model.js','view.js','app.js']);
  for(const resource of resources) yes(raw.has('prototype/'+resource),resource);
  for(const name of ['index.html','styles.css','demo-data.js','model.js','view.js','app.js']) {
    const source=raw.get('prototype/'+name).toString('utf8');
    eq(/https?:\/\/|\bfetch\s*\(|XMLHttpRequest|WebSocket|localStorage|sessionStorage|indexedDB/.test(source),false,name+' external/storage surface');
    if(name==='model.js') eq(/\b(?:parseFloat|parseInt|Number)\s*\(/.test(source),false,'no Number conversion in money model');
  }
});
test('CAL-21', 'all 46 inputs and snapshot unchanged after runtime tests', () => {
  for(const [name,bytes] of raw) eq(read(name).equals(bytes),true,name);
  eq(read(manifestPath).equals(manifestBytes),true,'snapshot bytes');
});
console.log(JSON.stringify({version:manifest.version,manifestSha256:expectedDigest,snapshotSha256:hash(manifestBytes),groupsPassed:passed,groupsFailed:failures.length,assertions,execution:'Node vm: actual demo-data/model/app, no DOM or browser',failures},null,2));
process.exitCode=failures.length?1:0;
