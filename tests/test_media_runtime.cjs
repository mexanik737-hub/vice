const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(new URL('../upstream/original/viceburg_mod_clean.html', `file://${__filename}`), 'utf8');
const start = source.indexOf('function pickNonRepeatingSexClip');
const end = source.indexOf('function sd(', start);
assert.ok(start >= 0 && end > start, 'dynamic media selector helpers can be extracted');
const helpers = source.slice(start, end);

let chooseMaximum = true;
const bridge = {
  listMediaFileNames(folder) {
    const files = folder === 'Characters/skin/Face'
      ? ['0', '2', '150', 'not-a-number']
      : ['0.mp4', '2.mp4', '150.mp4', 'notes.txt'];
    return JSON.stringify(files);
  },
  listMediaDirectoryNames() {
    return JSON.stringify(['0', '2', '150', 'not-a-number']);
  },
};
const context = {
  window: { ViceburgAndroid: bridge, SugarCube: { State: { variables: {} } } },
  QA(min, max) { return chooseMaximum ? max : min; },
};
vm.runInNewContext(helpers, context, { filename: 'dynamic-media-helpers.js' });

assert.deepEqual(Array.from(context.window.GetMediaFilesByPrefix('Events/foo', '', 'mp4')),
  ['Media/Events/foo/0.mp4', 'Media/Events/foo/2.mp4', 'Media/Events/foo/150.mp4']);
assert.equal(context.window.GetMediaIndexPick('Events/foo', 'mp4', 2), 'Media/Events/foo/150.mp4');
const resolved = context.window.resolveAvailableVideoPath('Media/Events/foo/1.mp4');
assert.ok(['0', '2', '150'].some(index => resolved === `Media/Events/foo/${index}.mp4`));
assert.deepEqual(Array.from(context.window.GetFaceTypeIndices('skin')), [0, 2, 150]);
assert.equal(context.window.GetFaceTypeIndex('skin', 2), 1);
assert.equal(context.window.GetFaceTypeAt('skin', 2), 150);
assert.equal(context.window.GetAdjacentFaceType('skin', 0, -1), 150);

context.window.ViceburgAndroid.listMediaFileNames = () => '[]';
assert.equal(context.window.GetMediaIndexPick('Events/missing', 'mp4', 2), 'Media/Events/missing/2.mp4',
  'empty discovery falls back to the legacy indexed range');
assert.equal(context.window.resolveAvailableVideoPath('Media/Events/missing/1.mp4'), 'Media/Events/missing/1.mp4',
  'unavailable discovery preserves the original video path');

console.log('Dynamic media selector tests passed.');
