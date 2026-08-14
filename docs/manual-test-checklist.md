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
| Shared unit tests | `./gradlew :shared:test` | PASS |
| Wear assemble | `./gradlew :wear:assembleDebug` | 產出 `wear-debug.apk` |
| Mobile assemble | `./gradlew :mobile:assembleDebug` | 產出 `mobile-debug.apk` |
| GitHub Actions | `.github/workflows/android-ci.yml` | assemble + shared tests |

## 手動項目（必跑才算完成）

| # | 項目 | 步驟 | 預期結果 | 結果欄 |
|---|------|------|----------|--------|
| 1 | **Consent gate** | 全新安裝 Wear App → 進入主畫面前應出現同意／法律範圍畫面 → 未同意不可進卡庫 | 必須同意 `own_only` 範圍；拒絕則停留／退出 | ☐ |
| 2 | **手動 UID 增刪改** | Wear：新增 UID-only 卡 → 編輯標籤／UID → 刪除 | 清單正確更新；詳情頁明示「無法模擬此門禁／僅備份」 | ☐ |
| 3 | **手機掃描 UID + sync** | Mobile 掃描實體卡 → 同步至配對手錶 | Wear 出現卡片；DESFire／Classic 標示誠實狀態，非「可克隆」 | ☐ |
| 4 | **備份匯出再匯入** | Wear 或 Mobile：設定密碼匯出 `.strawnfc` → 清空／重裝後匯入 | round-trip 還原卡片；錯誤密碼失敗；不含 Classic 明金鑰 | ☐ |
| 5 | **Tile 開啟預設卡** | 新增 Wear Tile → 點擊最愛卡 Tile | 開啟「準備模擬」畫面；**不**顯示「已開門」 | ☐ |
| 6 | **NDEF HCE** | 有硬體：NDEF 卡標 `SUPPORTED` 時啟用 HCE，讀卡機讀 Type 4；無硬體：跑 `:shared:test` APDU + 模擬器 | APDU handler 正確；UI 不宣稱門禁已開 | ☐ 單元／☐ 實機 |
| 7 | **DESFire unsupported** | 掃描／匯入 DESFire（或 IsoDep 摘要）→ 開 Emulate／詳情 | `PROTOCOL_UNSUPPORTED`；文案：只備份識別資訊，**不可克隆** | ☐ |

## 誠實矩陣抽查（驗收必看）

| 檢查 | 通過條件 |
|------|----------|
| 無假開門文案 | UI／字串／Tile／Complication **無**「已開門」「已解鎖門禁」成功宣稱 |
| UID-only | Emulate 狀態為 `DEVICE_UNSUPPORTED`（Stock HCE） |
| Classic | `PROTOCOL_UNSUPPORTED`／僅備份 |
| DESFire | `PROTOCOL_UNSUPPORTED`；明確不可克隆 |
| NDEF | 僅在 probe + payload 時 `SUPPORTED`；讀到 NDEF ≠ 門禁已開 |
| 法律 | Consent + `legal-scope.md`：`own_only`；無破解／交通／支付複製 |

## 實測註記（SNFC4 worker）

| 環境 | 結果 |
|------|------|
| 本機 CI 同等命令 | `export ANDROID_HOME=/opt/android-sdk; ./gradlew :shared:test :wear:assembleDebug :mobile:assembleDebug --no-daemon` |
| NDEF APDU | shared unit tests（`Type4NdefApduHandlerTest`）涵蓋；無 Wear 硬體時以單元測試＋證據 JSON 記錄 |
| 實機 NFC／門禁 | 本 worker 環境無實體讀卡機；項目 1–7 實機欄位交 OpenCode／現場驗收勾選 |
| 假開門宣稱 | 程式與文件抽查：**無**；能力矩陣誠實 |

最終階段 `PASS` 僅由 Hermes `trigger-verify` + OpenCode acceptance APPROVE 標記；worker 證據 JSON 的 `status=PASS` 僅代表交付物與局部驗證完成，非自行關閉長任務。
