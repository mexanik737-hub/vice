# New game prototype progress

## Goal

Build the first installable Android vertical slice from `docs/GAME_MECHANICS.md` on `codex/vice-v1-prototype`. Keep the legacy Twine app available in the repository while the new offline game is built as a separate domain module and Android screen.

## Requirements in this slice

- `V1-CHAR-01`: Create an adult male or female player with the agreed body fields and start values.
- `V1-NPC-01`: Generate a stable population of 60 adults: 54 women, 6 men, including 27 female and 3 male students.
- `V1-NPC-02`: Generate role, name, age, body, character traits, social/combat values, sexual experience, preferences, boundaries and portrait-set ID.
- `V1-WORLD-01`: Start March 1, 2026 at 09:00, at the rented city apartment, with 1,000 ₽. Move through a small playable set of city locations; update NPC locations from their schedules.
- `V1-QUEST-01`: Complete the first quest by meeting a different-gender adult NPC and finishing two full shifts; award 5,000 ₽ once.
- `V1-SAVE-01`: Autosave the game locally after actions; retain the 60 NPC and RNG state across launch.
- `V1-MEDIA-01`: Let the user select Media through Android SAF, scan nested files without an ordinal cap, skip `Media/backup`, display matching character portraits without cropping, and export a unique JSON backup under `Media/backup`.
- `V1-APK-01`: Produce a debug APK that can be installed for hands-on user testing.

## Phases and status

1. Inspect current Android shell, source generation and SAF behaviour — done; legacy Twine remains available as source and is no longer the launch screen in this prototype.
2. Create the new-game domain and first scenario — implemented in source; build validation pending.
3. Connect the Android screen, local save, Media picker and backup export — implemented in source; device/Media validation pending.
4. Build a debug APK and deliver it from the new branch — not yet verified. The local machine has no JDK, Gradle or Android SDK; the repository's Android preview workflow will build the debug APK when the branch is pushed.
5. Install on the user's Android device and collect feedback — waiting for the APK and user's device test.

## Scope still outside this slice

The full location catalogue and career ladder, relationship stages, home invitations, consent-driven sexual scenes, full phone and inventory, housing and cars, criminal events, broader quests, and all interaction media pools are not implemented yet. The slice includes three starting vacancies and monthly proration for that single job assignment. Portrait support follows `Персонажи/{М|Ж}/Face/{M|F}_N_Face.ext`; the number has no fixed digit limit. The actual user Media library is not present in this checkout, so matching against its real filenames is unverified.

## Verification record

- No Android toolchain is available locally, so no local APK build has run.
- No tests have been run in this turn.
- A remote Actions build has not yet run.

