# Сборка и установка

## GitHub Actions (основной способ)

Добавьте в Settings → Secrets and variables → Actions следующие secrets:

- `ANDROID_KEYSTORE_BASE64` — содержимое release keystore в Base64.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Запустите workflow **Android APK** через Actions → Run workflow. При наличии ключа и всех секретов подписанный APK появится в артефакте `viceburg-offline-apk`. Keystore и пароли не включаются в исходники.

## Локальная сборка

Нужны Android Studio/JDK 17/Android SDK API 35 и Gradle 8.9. Сборка выполняется из `android/` командой `gradle :app:assembleDebug`. Release требует keystore и переменные окружения, перечисленные выше.

На телефоне установите APK, откройте Viceburg, нажмите меню ⋮ → «Выбрать папку Media» и укажите каталог `Media`. Импорт/экспорт игровых сохранений находится в том же системном меню.
