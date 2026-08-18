# StrawNFC 手動測試清單（MVP SNFC4）

> 實測步驟文件化。政策：`own_only`。**Stock HCE ≠ 任意 UID 門禁克隆**；不得宣稱「已開門」。
> 版本目標欄請填當次 `VERSION`（`a.b.c.d`）。能力矩陣：[`capability-matrix.md`](capability-matrix.md)。

**版本：** 見 repo 根 `VERSION`  
**套件：** `xyz.wastebase.strawnfc`  
**Debug APK：**
- Wear：`wear/build/outputs/apk/debug/wear-debug.apk`
- Mobile：`mobile/build/outputs/apk/debug/mobile-debug.apk`

## 自動化閘門（本機／CI）

| 項目 | 命令／產物 | 預期 |
|------|------------|------|
| 全模組 unit tests | `./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest` | PASS |
| Wear assemble | `./gradlew :wear:assembleDebug` | 產出 `wear-debug.apk` |
| Mobile assemble | `./gradlew :mobile:assembleDebug` | 產出 `mobile-debug.apk` |
| GitHub Actions | `.github/workflows/android-ci.yml` | assemble + shared／wear／mobile tests |
| Play 管線文件 | `docs/play-release.md` + `.github/workflows/play-internal-release.yml` | 有 Fastlane `internal_testing`；keystore 不在 git |
| Signed mobile AAB | `./gradlew :mobile:bundleRelease`（需 signing env） | `mobile/build/outputs/bundle/release/mobile-release.aab` |

## 手動項目（必跑才算完成）

| # | 項目 | 步驟 | 預期結果 | 結果欄 |
|---|------|------|----------|--------|
| 1 | **Consent gate** | 全新安裝 Wear App → 進入主畫面前應出現同意／法律範圍畫面 → 未同意不可進卡庫 | 必須同意 `own_only` 範圍；拒絕則停留／退出 | ☐ |
| 2 | **手動 UID 增刪改** | Wear：新增 UID-only 卡 → 編輯標籤／UID → 刪除 | 清單正確更新；詳情頁明示「無法模擬此門禁／僅備份」 | ☐ |
| 2b | **手錶本機貼卡掃描** | Wear「新增卡片」→「貼卡掃描」→ 貼實體卡 → 寫入手錶 | 有讀卡硬體：清單出現卡（type／UID 誠實）；無讀卡／僅 HCE：顯示明確提示且手動／手機路徑仍可用 | ☐ |
| 3 | **手機掃描即寫入手錶** | Mobile「掃描並寫入手錶」→ 貼卡 → 自動寫入配對手錶（可改名稱後再按「再次寫入手錶」） | Wear 清單出現卡片；未連線手錶時顯示明確錯誤；DESFire／Classic 誠實標示；與 2b 並存 | ☐ |
| 4 | **備份匯出再匯入** | Wear 或 Mobile：設定密碼匯出 `.strawnfc` → 清空／重裝後匯入 | round-trip 還原卡片；錯誤密碼失敗；不含 Classic 明金鑰 | ☐ |
| 5 | **Tile 開啟預設卡** | 新增 Wear Tile → 點擊最愛卡 Tile | 開啟「準備模擬」畫面；**不**顯示「已開門」 | ☐ |
| 5b | **圓形錶模擬畫面** | 詳情「準備模擬」或 Tile 進入模擬頁 | 標題／狀態／開始或「僅備份」在首屏可見（不必先滑過長文）；可捲動看誠實說明；圓形錶圈不裁掉畫面 | ☐ |
| 6 | **NDEF HCE** | 有硬體：NDEF 卡標 `SUPPORTED` 時啟用 HCE，讀卡機讀 Type 4；無硬體：跑 `:shared:test` APDU + 模擬器 | APDU handler 正確；UI 不宣稱門禁已開；啟動模擬時暫停讀卡並請求 preferred HCE；畫面上有 APDU 紀錄 | ☐ 單元／☐ 實機 |
| 6c | **APDU 診斷** | 開始模擬後用手機靠近手錶；看畫面 APDU 或 `adb logcat -s StrawNFC-HCE` | 無 `RX: 00 A4` → HCE／AID routing；有 SELECT／READ BINARY → routing 成功，再查 Type 4 CC／NDEF File | ☐ |
| 6b | **NFC 互斥／設定** | Wear：模擬中再開「貼卡掃描」應停 HCE；NFC 關閉時可開系統設定 | 讀卡與模擬不同時佔用；無法強制開 NFC 無線電 | ☐ |
| 6d | **停止後不得再回應** | 開始模擬 → 手機讀到 NDEF → 按「停止模擬」→ 手機再靠近 | 手機讀不到 NDEF（Reader 只得 `6985`／無標籤）；畫面不再顯示工作階段進行中 | ☐ |
| 6e | **換卡不得回舊 payload** | A 卡開始模擬 → 停止 → 返回選 B 卡開始模擬 → 手機讀取 | 讀到的是 B 卡 payload；APDU 紀錄從 B 卡的 SESSION start 起算 | ☐ |
| 6f | **NFC 設定返回** | 模擬頁顯示「系統 NFC 關閉」→ 進系統設定開啟 NFC → 返回 App | 回到模擬頁時狀態自動變為可模擬，不需重進畫面 | ☐ |
| 7 | **DESFire unsupported** | 掃描／匯入 DESFire（或 IsoDep 摘要）→ 開 Emulate／詳情 | `PROTOCOL_UNSUPPORTED`；文案：只備份識別資訊，**不可克隆** | ☐ |

## 誠實矩陣抽查（驗收必看）

| 檢查 | 通過條件 |
|------|----------|
| 無假開門文案 | UI／字串／Tile／Complication **無**「已開門」「已解鎖門禁」成功宣稱 |
| UID-only | Emulate 狀態為 `DEVICE_UNSUPPORTED`（Stock HCE） |
| Classic | `PROTOCOL_UNSUPPORTED`／僅備份 |
| DESFire | `PROTOCOL_UNSUPPORTED`；明確不可克隆 |
| NDEF | 僅在 probe + payload（可解碼且 ≤ 2048 bytes）時 `SUPPORTED`；讀到 NDEF ≠ 門禁已開 |
| 模擬頁標題 | 僅 `SUPPORTED` 的 NDEF 顯示「Type 4 NDEF」；其餘顯示「準備模擬」，不得對 UID／Classic／DESFire 暗示 Type 4 |
| 法律 | Consent + `legal-scope.md`：`own_only`；無破解／交通／支付複製 |

## 實測註記（SNFC4 worker）

| 環境 | 結果 |
|------|------|
| 本機 CI 同等命令 | `export ANDROID_HOME=/opt/android-sdk; ./gradlew :shared:test :wear:assembleDebug :mobile:assembleDebug --no-daemon` |
| NDEF APDU | shared unit tests（`Type4NdefApduHandlerTest`）涵蓋；無 Wear 硬體時以單元測試＋證據 JSON 記錄 |
| 實機 NFC／門禁 | 本 worker 環境無實體讀卡機；項目 1–7 實機欄位交 OpenCode／現場驗收勾選 |
| 假開門宣稱 | 程式與文件抽查：**無**；能力矩陣誠實 |

最終階段 `PASS` 僅由 Hermes `trigger-verify` + OpenCode acceptance APPROVE 標記；worker 證據 JSON 的 `status=PASS` 僅代表交付物與局部驗證完成，非自行關閉長任務。
