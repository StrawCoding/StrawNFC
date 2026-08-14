# StrawNFC — Play Store 自動上架（internal track）

產品：**StrawNFC**。套件：**`xyz.wastebase.strawnfc`**。唯一分支：`main`。

上架**不改變**產品邊界：`own_only`、Stock HCE ≠ 任意 UID 門禁克隆、不做破解／交通／支付卡複製、DESFire **不可克隆**。詳見 [`legal-scope.md`](legal-scope.md) 與 [`capability-matrix.md`](capability-matrix.md)。

本文件不寫入任何密碼、`.jks` 內容、Gmail 密碼或 Play JSON 正文。

## 管線概觀

`v*` git tag → GitHub Actions [`.github/workflows/play-internal-release.yml`](../.github/workflows/play-internal-release.yml) → restore upload keystore → `./gradlew :mobile:bundleRelease :wear:bundleRelease` →（若有）restore Play JSON → Fastlane lane **`internal_testing`**（`track=internal`）。

對照 StrawMoneyBook 的 signing → bundleRelease → Fastlane → tag 觸發，改寫為 StrawNFC 多模組（`:mobile` / `:wear`）。

**本階段目標是管線就緒。** 在 `PLAY_SERVICE_ACCOUNT_JSON` 設定前，workflow 仍須在有 keystore secrets 時建出 **signed AAB**；上傳步驟會明確失敗（見下方）。不要由 worker 自行打正式 release tag 上傳，也不要自行 mark Hermes PASS。

## GitHub Secrets

| Secret | 用途 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | Upload keystore 的 base64（Hermes 已寫入；**勿覆寫指紋**） |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore 密碼 |
| `ANDROID_KEY_ALIAS` | 金鑰別名（預期 `strawnfc-upload`） |
| `ANDROID_KEY_PASSWORD` | 金鑰密碼 |
| `ANDROID_PACKAGE_NAME` | 必須為 `xyz.wastebase.strawnfc` |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console **服務帳戶 JSON**（**尚未**設定時，上傳步驟失敗；**不能**用 Gmail 密碼） |

私鑰只存在 CI secret 與離線備份；**禁止** commit `.jks` / `keystore.properties` / Play JSON。

公開 upload 憑證（非私鑰）供 Play App Signing 對帳；SHA-256 指紋必須與 Hermes 產生的 upload 憑證一致：

`EA:6B:C8:F8:4A:08:B2:6A:99:24:15:FE:32:91:47:4A:4E:B0:3C:BE:1F:7F:20:C5:EE:93:34:B0:F8:5A:9F:B2`

若指紋不符，**停止並回報**，不要自行重產 keystore。

## 本機 signed bundle（驗證用）

需要本機 upload keystore 路徑時，用環境變數指向 Hermes 備份（路徑勿提交進 git）：

```bash
export ANDROID_HOME=/opt/android-sdk
export ANDROID_KEYSTORE_FILE  # 本機 .jks 絕對路徑（不在 repo）
export ANDROID_KEYSTORE_PASSWORD
export ANDROID_KEY_ALIAS=strawnfc-upload
export ANDROID_KEY_PASSWORD

./gradlew :mobile:bundleRelease --no-daemon
# 產物：mobile/build/outputs/bundle/release/mobile-release.aab

./gradlew :wear:bundleRelease --no-daemon
# 產物：wear/build/outputs/bundle/release/wear-release.aab
```

未設定上述 env 時，`:mobile:bundleRelease` / `:wear:bundleRelease` **會失敗**（避免誤用 debug 金鑰上傳到 Play）。`:mobile:assembleRelease` / `:wear:assembleRelease` 在缺簽章 env 時仍可產出 **unsigned** release APK，供本機／Hermes 編譯驗證（targetSdk／versionCode 仍寫入 manifest）；**不可**拿 unsigned APK／AAB 上傳 Play。Debug 組建不需要這些變數。

可選本機檔 `keystore.properties`（已 gitignore），鍵名：`storeFile`、`storePassword`、`keyAlias`、`keyPassword`。不要把填好的檔案提交。

## Phone + Wear（同一套件）

| 模組 | 角色 | `applicationId` | Play 交付 | versionCode |
|------|------|-----------------|-----------|-------------|
| `:mobile` | 薄 Companion（掃描＋同步） | `xyz.wastebase.strawnfc` | 手機 AAB | `(base)*2`（偶數） |
| `:wear` | 主 App（清單／Tile／HCE） | `xyz.wastebase.strawnfc` | 手錶 AAB（`android.hardware.type.watch` + standalone） | `(base)*2+1`（奇數） |

`base` = `major*1_000_000 + minor*10_000 + patch*100 + preview`（`VERSION` a.b.c.d）。兩顆 AAB 必須不同 versionCode，Play 才能在同一 listing 做 Phone + Wear。

**不要**把 Wear 嵌進 phone APK（舊版 `wearApp`）；Wear OS standalone 以獨立 AAB 上傳。

### Wear OS 必須走專用軌道（SNFC8）

失敗範例（tag `v0.1.0.16`，run [31815650233](https://github.com/StrawCoding/StrawNFC/actions/runs/31815650233)）：

`Google Api Error: Server error - Internal error encountered.`

當時 Fastlane 把 **phone + wear 兩顆 AAB 一次塞進手機軌道 `internal`（`aab_paths`）**。自 2023-08／09 起，Play 要求 Wear OS 使用**專用 form-factor 軌道**；把 Wear bundle 掛在手機軌道並 commit 時，API 常回含糊的 **Internal error**（Console 手動上傳時則可能寫明要改用 Wear track）。

現行上傳路徑（lane `internal_testing`）：

| 產物 | Play track | 環境變數 |
|------|------------|----------|
| `:mobile` AAB | `internal` | `PLAY_INTERNAL_TRACK`（預設 `internal`） |
| `:wear` AAB | `wear:internal` | `PLAY_WEAR_INTERNAL_TRACK`（預設 `wear:internal`） |

兩次獨立的 `upload_to_play_store`（各一顆 `aab`），**禁止**再把 wear 與 phone 合成同一個 `aab_paths` 上傳到手機軌道。

Play Console 需先啟用 Wear 專用軌道：Test and release → Advanced settings → Form factors → Wear OS → **Use a dedicated release track for Wear OS**。若軌道尚未建立，API 會回 `Track not found: wear:internal`（比舊的 Internal error 清楚）；建立後再跑 `v*` workflow。**在啟用前不要**把 Wear AAB 塞回手機 `internal`（那正是 `v0.1.0.16` Internal error 的根因）。

本機已驗證（2026-08-14）：分軌後 phone → `internal` + `release_status=draft` **可成功 commit**；Console `internal` 可見 `versionName=0.1.0.16`、`versionCodes=[20032]`，不再是空 draft。同次探測：套件尚無任何 `wear:*` 軌道，Wear 上傳會明確失敗並提示啟用專用軌道（可選 `PLAY_WEAR_OPTIONAL=true` 僅略過 Wear，正式 CI 預設仍要求 Wear 軌道存在）。

官方說明：[Manage different form factor releases on dedicated tracks](https://support.google.com/googleplay/android-developer/answer/13295490)、[Android Publisher tracks（`wear:` 前綴）](https://developers.google.com/android-publisher/tracks#ff-track-name)。社群／Console 對內部測試軌道 ID 使用 `internal`／`wear:internal`（文件表偶寫 `qa`／`wear:qa`；本專案與 Fastlane 實務對齊 `internal`）。

CI 可用 `CI_VERSION_NAME`（由 tag `v*` 去掉 `v`）覆寫 `versionName`。

### Target SDK（Play 上傳硬門檻）

失敗範例（tag `v0.1.0.13`，run [31811243405](https://github.com/StrawCoding/StrawNFC/actions/runs/31811243405)）：

`Google Api Error: Invalid request - Target SDK of artifact is too low: 20026.`

此處 **`20026` 是 phone AAB 的 versionCode**（`0.1.0.13` → base `10013` → phone `*2`），不是 targetSdk 數值。真正問題是當時 `targetSdk=34`，低於 Play 對手機／平板更新的最低要求。

現行（見 [Play Console Help — Target API level](https://support.google.com/googleplay/android-developer/answer/11926878)）：

| 表單因素 | 更新／新版上傳最低 targetSdk | 生效日 |
|----------|------------------------------|--------|
| Phone／tablet（本 listing 的 `:mobile`） | **API 35**；自 **2026-08-31** 起 **API 36** | 35：2025-08-31；36：2026-08-31 |
| Wear OS（`:wear`） | **API 34**；自 **2026-08-31** 起 **API 35+** | 34：2025-08-31；35：2026-08-31 |

`gradle/libs.versions.toml` 將 `compileSdk`／`targetSdk` 設為 **36**（兩模組共用），並使用 **AGP ≥ 8.9.1**（官方對 API 36 的最低 AGP）。勿為通過上傳而拆掉 wear 或改 package name。

## Fastlane / Ruby

- 目錄：repo 根 `fastlane/Fastfile`、`Gemfile`、`Gemfile.lock`
- Lane：`internal_testing`（phone=`PLAY_INTERNAL_TRACK` 預設 `internal`；wear=`PLAY_WEAR_INTERNAL_TRACK` 預設 `wear:internal`）
- Play **edit conflict** 最多重試 3 次、間隔 15 秒；若 draft app 拒收 `completed`／或 commit 出現 Internal error，會自動再試 `release_status=draft`
- 需要 `PLAY_JSON_KEY_PATH` 指向服務帳戶 JSON 檔；缺檔時錯誤訊息會指向本文件
- `PLAY_RELEASE_STATUS` 預設 **`completed`**（內部測試員可下載 phone／wear）。僅要 Console 可見、不對測試員開放時再設 `draft`。若 draft app／listing 未齊導致 API 拒收 `completed`，Fastlane 會自動 fallback 到 `draft`（見上）
- 本機 dry-run：`PLAY_VALIDATE_ONLY=true`（走 Play `validateOnly`，不留下正式提交）

### Ruby／Bundler（CI 必看）

- Workflow [`.github/workflows/play-internal-release.yml`](../.github/workflows/play-internal-release.yml) 使用 **Ruby 3.3**（`ruby/setup-ruby` + `bundler-cache: true`）。
- `Gemfile` 宣告 `ruby ">= 3.3.0"`。現行 fastlane 依賴鏈含 **excon ≥ 1.7**，該 gem 要求 Ruby ≥ 3.3；**不可**把 CI 鎖回 3.2，否則 `bundle install` 會失敗（見 run `31809417449`）。
- 變更依賴後請在 Ruby ≥ 3.3 環境執行 `bundle lock`（必要時 `bundle lock --add-platform ruby x86_64-linux`）並 **commit `Gemfile.lock`**。勿只改 `Gemfile` 不更新 lock。
- 本機若遇 native gem（如 `nkf`）因 GCC 15／舊 binutils 編譯失敗，請改用 `gcc-13`（或 Docker `ruby:3.3`）再 `bundle install`；GitHub-hosted runner 不受此影響。

本機（有 JSON 檔時；本階段通常沒有）：

```bash
# 需要 Ruby >= 3.3 與與 lockfile 相容的 bundler
bundle install
export ANDROID_PACKAGE_NAME=xyz.wastebase.strawnfc
export PLAY_JSON_KEY_PATH  # 服務帳戶 JSON 路徑，不在 repo
export AAB_PATH="$PWD/mobile/build/outputs/bundle/release/mobile-release.aab"
export WEAR_AAB_PATH="$PWD/wear/build/outputs/bundle/release/wear-release.aab"
# 可選：PLAY_RELEASE_STATUS=draft|completed ；PLAY_VALIDATE_ONLY=true
bundle exec fastlane internal_testing
```

## Workflow 行為（secret 缺席）

| 條件 | 行為 |
|------|------|
| 缺 keystore secrets | 建置前失敗；無法產出 signed AAB |
| 有 keystore、缺 `PLAY_SERVICE_ACCOUNT_JSON` | **仍建出 signed AAB**，上傳為 Actions artifact 與 GitHub Release，然後 **Restore Play service account 明確失敗**（此為預期，直到 Hermes 寫入 JSON） |
| 兩者皆有 | Fastlane 上傳 internal track |

Gmail／Google 帳號密碼**不能**當作 Play API 憑證。

## 首次上傳（人工／Hermes）

1. Play Console 建立應用程式，套件名 **`xyz.wastebase.strawnfc`**，form factors：**Phone + Wear OS**。
2. Advanced settings → Form factors → Wear OS → 啟用 **dedicated Wear OS release track**（否則 `wear:internal` 會 Track not found）。
3. 啟用 Play App Signing；upload 憑證須對應 Hermes 已產生的 upload key（指紋見上）。**不要重產／覆寫**既有 upload keystore。
4. 建立 Play Console 服務帳戶、授權 Android Publisher，把 JSON 寫入 GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON`。
5. 補齊商店文案／圖示（own_only 與誠實能力聲明；禁止「任意門禁可開」）。
6. 確認 `VERSION`，`git tag v<VERSION>` 並 push tag（**僅 Hermes／維護者**；本 worker 不打上傳用 tag）。
7. 內部測試：phone 看 **Internal testing**；wear 看 **Wear OS → Internal testing**。CI 預設 `PLAY_RELEASE_STATUS=completed`（測試員可下載）。若需只進 Console 不對測試員開放，再改回 `draft`。
8. **測試員必須掛在「有 completed 版本」的軌道上。** 常見踩坑（2026-08-15 實測）：
   - App 仍是 **draft app** 時，`alpha`／`wear:alpha` **只能**放 `draft` release（API：`Only releases with status draft may be created on draft app`）→ 測試員在 closed track **看不到／裝不到**。
   - 可下載的版本在 `internal`／`wear:internal`（completed），但若 Console 把 internal 升級成 open/closed testing，Publisher API **無法**用 `googleGroups` 掛測試員（`switch back to communities-based testing`）。
   - 若 Google Group 只掛在 phone `alpha`、沒掛在 **Wear OS → Internal testing** 測試員，手錶 Play 一樣裝不到（與版本 draft/completed 無關）。
   - **Console 必做**：Test and release → **Wear OS** → Internal testing → Testers → 加入同一 Google Group／email list（例如 `strawmoneybooktester@googlegroups.com`），並讓測試帳號開啟 opt-in。
   - App 離開 draft、closed testing 可放 completed 之後，改走 SMB 同款：phone=`alpha` + wear=`wear:alpha`（群組已可 API 掛上）。目前已建立 `wear:alpha` 並掛上群組，但在 draft app 期間 release 只能是 draft。

## 版本與 tag

- 原始碼改動：`bash scripts/bump-version.sh`
- Play／GitHub Release tag 格式：`v` + `VERSION`（例如 `v0.1.0.12`）
- StrawNFC 的 `*.0` **是合法 release preview=0**，workflow **不會**因為 tag 以 `.0` 結尾而跳過（與 StrawMoneyBook 的 skip 規則不同）

可選 `release-notes.pending.md`（`#` 與 `---` 之前的行會進 Play changelog，上限 500 字）。

## 誠實能力（上架後仍適用）

- **own_only**：僅自己擁有／已授權的卡。
- Stock Wear HCE **通常無法改寫對外 UID**；不得宣稱任意 UID 門禁可開。
- DESFire：僅識別摘要，**不可克隆**。
- `:mobile` 保持極薄 Companion（掃描＋同步），不以商店版「解鎖」更多門禁能力。
