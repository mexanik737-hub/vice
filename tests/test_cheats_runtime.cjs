const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(new URL('../upstream/original/viceburg_mod_clean.html', `file://${__filename}`), 'utf8');
const start = source.indexOf('function maxCheatFields(');
const end = source.indexOf('function toolsDialog(', start);
assert.ok(start >= 0 && end > start, 'max-cheat helpers can be extracted');
const context = {};
vm.runInNewContext(`${source.slice(start, end)};globalThis.applyMaxCheats=applyMaxCheats;`, context);

const target = {
  money: 50,
  status: { energy: { value: 7, max: 100 }, scalar: 12 },
  attributes: { strength: { value: 2, max: 20 } },
  progression: { strength: { value: 4, max: 10 } },
  sexExperience: { vaginal: { value: 1, max: 99, level: 2 } },
  body: { breastSize: { value: 3, max: 10 } },
  behavior: { mood: { confidence: { value: 2, max: 100 } } },
};
target.body.self = target.body;

assert.equal(context.applyMaxCheats(target), true);
assert.equal(target.money, 1_000_000);
assert.equal(target.status.energy.value, 100);
assert.equal(target.status.scalar, 12);
assert.equal(target.attributes.strength.value, 20);
assert.equal(target.progression.strength.value, 10);
assert.equal(target.sexExperience.vaginal.value, 99);
assert.equal(target.sexExperience.vaginal.level, 99);
assert.equal(target.body.breastSize.value, 10);
assert.equal(target.behavior.mood.confidence.value, 100);

console.log('Cheat max-stat tests passed.');
