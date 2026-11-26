from pathlib import Path
import hashlib, json, re, subprocess, sys, unittest
import html as html_lib
ROOT=Path(__file__).resolve().parents[1]
class ProjectTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  subprocess.run([sys.executable,str(ROOT/'tools/build_game.py')],check=True,capture_output=True,text=True)
  cls.original=(ROOT/'upstream/original/viceburg_mod_clean.html').read_bytes()
  cls.game=(ROOT/'android/app/src/main/assets/game/index.html').read_text(encoding='utf-8')
 def test_original_and_runtime_game_markers(self):
  self.assertIn('tw-storydata',self.game)
  self.assertIn('vbModCaptureGeneration',self.game)
  self.assertIn('vbModCheats',self.game)
  self.assertIn('Media/',self.game)
  self.assertIn('viceburg-ru-layer',self.game)
 def test_localization_startup_skips_embedded_story_data(self):
  runtime=(ROOT/'tools/build_game.py').read_text()
  self.assertIn("for(const root of document.querySelectorAll('#passages,#ui-bar,#ui-dialog'))translate(root);",runtime)
  self.assertNotIn('translate(document.documentElement)',runtime)
  self.assertIn('requestAnimationFrame(flush)',runtime)
 def test_translation_catalog_well_formed(self):
  c=json.loads((ROOT/'localization/catalog/ru.json').read_text())
  progress=json.loads((ROOT/'localization/progress.json').read_text())
  self.assertEqual(c['meta']['language'],'ru')
  self.assertTrue(c['phrases'])
  self.assertTrue(all(isinstance(k,str) and isinstance(v,str) for k,v in c['phrases'].items()))
  self.assertEqual(progress['catalog']['exact_phrase_count'],len(c['phrases']))
  self.assertEqual(progress['catalog']['dynamic_template_count'],len(c['templates']))
  rows=[json.loads(line) for line in (ROOT/'localization/inventory/candidates.jsonl').read_text(encoding='utf-8').splitlines()]
  faq={row['source'] for row in rows if row['passage']=='FAQDialog'}
  self.assertTrue(faq)
  self.assertTrue(faq.issubset(c['phrases'].keys()), sorted(faq-set(c['phrases']))[:10])
 def test_android_is_offline_and_has_saf(self):
  manifest=(ROOT/'android/app/src/main/AndroidManifest.xml').read_text()
  activity=(ROOT/'android/app/src/main/java/ru/viceburg/offline/MainActivity.kt').read_text()
  self.assertNotIn('android.permission.INTERNET',manifest)
  self.assertIn('ACTION_OPEN_DOCUMENT_TREE',activity)
  self.assertIn('shouldInterceptRequest',activity)
  self.assertIn('application/octet-stream',activity)
  self.assertIn('mimeFromName(uri.lastPathSegment)',activity)
  self.assertIn('Range',activity)
  self.assertIn('Content-Range',activity)
  self.assertIn('createDirectory("backup")',activity)
  self.assertIn('createFile("application/json", fileName)',activity)
  self.assertIn('takePersistableUriPermission(uri, flags)',activity)
  self.assertIn('Media/backup/',activity)
  self.assertIn('fun listMediaMp4Indices(relativePath: String): String',activity)
  self.assertIn('fun listMediaFileNames(relativeDirectory: String): String',activity)
  self.assertIn('fun listMediaDirectoryNames(relativeDirectory: String): String',activity)
  self.assertIn('Regex("^(\\\\d+)\\\\.mp4$", RegexOption.IGNORE_CASE)',activity)
  self.assertIn('ACTION_OPEN_DOCUMENT',activity)
 def test_game_media_request_patterns_are_supported(self):
  source=html_lib.unescape((ROOT/'upstream/original/viceburg_mod_clean.html').read_text())
  video=re.search(r'<<widget "Video">>(.*?)<</widget>>',source,re.S)
  self.assertIsNotNone(video)
  self.assertIn('<video controls autoplay muted playsinline',video.group(1))
  self.assertNotIn(' loop ',video.group(1))
  self.assertIn('function pickNonRepeatingSexClip(paths,key)',source)
  self.assertIn('pickNonRepeatingSexClip(d.map(index=>cd(l,t,c,h,index))',source)
  self.assertIn('historyByPool[poolKey]=[...recent,picked].slice(-8)',source)
  self.assertIn('function sexClipIndices(folder,fallbackMax)',source)
  self.assertIn('listMediaMp4Indices(folder)',source)
  self.assertIn('sexClipIndices(`Sex/${rd(e,t,n,a)}`,o.max)',source)
  self.assertIn('sexClipIndices(`Sex/${a.folder}`,a.skins.length-1)',source)
  self.assertIn('function mediaFileNames(folder)',source)
  self.assertIn('function mediaFamilyPaths(folder,prefix,extension)',source)
  self.assertIn('window.GetMediaIndexPick=pickMediaIndex',source)
  self.assertIn('window.resolveAvailableVideoPath=resolveAvailableVideoPath',source)
  self.assertIn('window.GetMediaFilesByPrefix=mediaFamilyPaths',source)
  self.assertIn('window.GetFaceTypeIndices=faceTypeIndices',source)
  self.assertIn('function faceTypeIndices(skin)',source)
  self.assertNotRegex(source,r"Media\\\\[^'\"]{0,120}Random\(0,")
  self.assertIn('replace(/\\\\/g, \'/\')',video.group(1))
  self.assertIn('<<widget "SetBackground">>',source)
  self.assertIn('<<widget "DrawImage">>',source)
  self.assertIn('new Audio(e)',source)
 def test_synthetic_media_fixtures_exist(self):
  fixtures=ROOT/'tests/fixtures/Media'
  self.assertTrue((fixtures/'Vehicles/compact-car.jpg').is_file())
  self.assertTrue((fixtures/'Events/Diner Events/flirt-diner.webm').is_file())
  self.assertTrue((fixtures/'Audio/Weather/rain1.wav').is_file())
  self.assertTrue((ROOT/'tests/fixtures/index.html').is_file())
 def test_cheats_edit_real_character_parameters_and_npc_cooldown(self):
  source=html_lib.unescape((ROOT/'upstream/original/viceburg_mod_clean.html').read_text(encoding='utf-8'))
  for marker in [
   "addLimitedFields(status,target.status",
   "addLimitedFields(skills,target.attributes",
   "addLimitedFields(progression,target.progression",
   "addHiddenFields(hidden,target.sexExperience",
   "addHiddenFields(hidden,target.body",
   "addHiddenFields(hidden,target.behavior?.mood",
  ]: self.assertIn(marker,source)
  for key in ['horny','drugIntoxication','potency','lust','sexStamina','corruption','submission','firearmProficiency','analSkill']:
   self.assertIn(key,source)
  for key in ['friendship','love','sex','fear','anger','corruption']:
   self.assertIn("'"+key+"'",source)
  cooldown=re.search(r'MarkNpcInitiatedEventTriggered=function\((.*?)\},window\.CharactersRefresh',source)
  self.assertIsNotNone(cooldown)
  self.assertIn('getMinutes()+20',cooldown.group(1))
  self.assertNotIn('getMinutes()+30',cooldown.group(1))
 def test_faq_localization_is_attached_to_existing_dom(self):
  catalog=json.loads((ROOT/'localization/catalog/ru.json').read_text())
  self.assertIn('What is on the sidebar?',catalog['phrases'])
  self.assertIn('data-faq-label',self.game)
  self.assertIn("'data-faq-label'",self.game)
  self.assertTrue(any(x['selector']=='.faq-intro' for x in catalog['scoped']))
  source=(ROOT/'upstream/original/viceburg_mod_clean.html').read_text(encoding='utf-8')
  passage=re.search(r'<tw-passagedata\b[^>]*\bname="FAQDialog"[^>]*>(.*?)</tw-passagedata>',source,re.S).group(1)
  passage=html_lib.unescape(passage)
  questions=[html_lib.unescape(re.sub(r'<[^>]+>','',x)).strip() for x in re.findall(r'<div class="faq-question">(.*?)</div>',passage,re.S)]
  self.assertGreaterEqual(len(questions),70)
  self.assertTrue(all(q in catalog['phrases'] for q in questions),[q for q in questions if q not in catalog['phrases']])
 def test_asset_does_not_corrupt_identifiers(self):
  source=(ROOT/'upstream/original/viceburg_mod_clean.html').read_text(encoding='utf-8')
  # Git checkout settings may convert LF to CRLF on Windows; hash the canonical LF form from the source manifest.
  canonical_original=self.original.replace(b'\r\n',b'\n')
  self.assertEqual(hashlib.sha256(canonical_original).hexdigest(),json.loads((ROOT/'localization/progress.json').read_text())['source_sha256'])
  self.assertIn('vbModCheats',source); self.assertIn('vbModCaptureGeneration',source)
  self.assertEqual(source.count('<tw-passagedata'),self.game.count('<tw-passagedata'))
  names=lambda text: re.findall(r'<tw-passagedata\b[^>]*\bname="([^"]+)"',text)
  self.assertEqual(names(source),names(self.game))
  raw_passages=lambda text: re.findall(r'<tw-passagedata\b[^>]*>(.*?)</tw-passagedata>',text,re.S)
  self.assertEqual(raw_passages(source),raw_passages(self.game))
  media_paths=lambda text: re.findall(r'Media/[A-Za-z0-9_ ./()\-]+',text)
  self.assertEqual(media_paths(source),media_paths(self.game))
if __name__=='__main__':unittest.main()
