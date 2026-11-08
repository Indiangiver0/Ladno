// Author smoke check of the display model against independently authored finance fixtures.
// This executes no DOM, browser, Android WebView, camera or UI interaction.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../../..');
const source = 'android-app/app/src/main/assets/www/view.js';
const fixture = 'docs/android/APP-008-CHART-FIXTURES.json';
function read(name) { return fs.readFileSync(path.join(root, name)); }
function hash(name) { return crypto.createHash('sha256').update(read(name)).digest('hex'); }
function subset(actual, expected, location) {
  Object.keys(expected).forEach(function (key) {
    const at = location + '.' + key, value = expected[key];
    if (value !== null && typeof value === 'object') {
      if (!actual || actual[key] === undefined) throw new Error('Missing ' + at);
      if (Array.isArray(value) && actual[key].length !== value.length) throw new Error('Length ' + at);
      subset(actual[key], value, at);
    } else if (!actual || actual[key] !== value) throw new Error(at + ': expected ' + String(value) + ', actual ' + String(actual && actual[key]));
  });
}
const context = { window: { document: {}, BigInt: BigInt } };
vm.createContext(context);
const before = { source: hash(source), fixtures: hash(fixture) };
vm.runInContext(read(source).toString('utf8'), context);
const fixtures = JSON.parse(read(fixture).toString('utf8'));
const results = [];
fixtures.cases.concat(fixtures.invalidCases).forEach(function (test) {
  const actual = context.window.AppView.chartModel(test.state);
  const result = { id: test.id, expected: test.expected, actual: actual, passed: false };
  try { subset(actual, test.expected, test.id); result.passed = true; }
  catch (error) { result.error = error.message; }
  results.push(result);
});
context.window.BigInt = undefined;
const unavailable = context.window.AppView.chartModel(fixtures.cases[1].state);
results.push({ id: 'NO-BIGINT', expected: 'unavailable', actual: unavailable.status, passed: unavailable.status === 'unavailable' });
if (before.source !== hash(source) || before.fixtures !== hash(fixture)) throw new Error('Source or fixture changed during check');
const report = {
  task: 'APP-008', author: '/root/improve_interface', executedAtUtc: new Date().toISOString(),
  command: 'node android-app/qa/app008-interface-author/chart_smoke.js',
  scope: 'Pure display model author smoke, not independent QA or browser/Android UI',
  source: { path: source, sha256: before.source }, fixtures: { path: fixture, sha256: before.fixtures },
  passed: results.filter(x => x.passed).length, total: results.length, results: results
};
fs.writeFileSync(path.join(__dirname, 'results.json'), JSON.stringify(report, null, 2) + '\n');
fs.writeFileSync(path.join(__dirname, 'tested-source-hashes.json'), JSON.stringify({
  [source]: before.source,
  'android-app/app/src/main/assets/www/styles.css': hash('android-app/app/src/main/assets/www/styles.css'),
  'android-app/app/src/main/assets/www/index.html': hash('android-app/app/src/main/assets/www/index.html'),
  [fixture]: before.fixtures
}, null, 2) + '\n');
console.log(report.passed + '/' + report.total + ' author model checks PASS');
results.filter(x => !x.passed).forEach(x => console.log(x.id + ': ' + x.error));
process.exitCode = report.passed === report.total ? 0 : 1;
