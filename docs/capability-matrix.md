# StrawNFC 能力矩陣

> 產品誠實聲明：**Stock HCE ≠ 任意 UID 門禁克隆**。本表定義 MVP 行為；「可行」指協定／常見硬體路徑，「不可」為產品明確拒絕或技術上不支援，「機型依賴」須經能力探測後才啟用。

政策：`own_only`（僅自己擁有／已授權的卡）。詳見 [`legal-scope.md`](legal-scope.md)。

**SNFC3 實作狀態（Backup＋Tile＋誠實 HCE）：** `CapabilityProbe` + `StrawHostApduService`（NDEF Type 4 AID `D2760000850101`）已落地；UID／Classic／DESFire 在 Emulate UI **誠實標示不可／機型依賴**，永不宣稱「已開門」。

**SNFC4 驗收狀態（CI＋手動清單）：** `.github/workflows/android-ci.yml`（`:shared:test` + Wear／Mobile `assembleDebug`）；手動步驟見 [`manual-test-checklist.md`](manual-test-checklist.md)。Debug APK：`wear/build/outputs/apk/debug/wear-debug.apk`、`mobile/build/outputs/apk/debug/mobile-debug.apk`。證據：`tests/strawnfc/output/snfc4-ci-acceptance.json`。**無假開門宣稱**；最終 PASS 僅 Hermes verify + OpenCode APPROVE。

**SNFC5（Play 上架管線）：** [`docs/play-release.md`](play-release.md)。商店上架**不放寬**本矩陣；internal track 發布仍是備份＋誠實模擬，不是「任意門禁可開」。

## 矩陣（讀取 × 儲存 × 模擬）

| 卡類型 | 讀取 | 儲存 | 模擬（HCE／模擬使用） | 說明 |
|--------|------|------|------------------------|------|
| **UID-only** | **可行**（手機或手錶 NFC `Tag.id`；無讀卡硬體時可手動輸入） | **可行**（含加密 `.strawnfc` 備份） | **機型依賴 → MVP 標 `DEVICE_UNSUPPORTED`** | 可備份 UID。Stock Wear Host Card Emulation **通常無法改寫對外 UID**；多數 UID 門禁讀器驗的是 UID，因此**不能宣稱任意門禁可開**。UI：「此裝置無法模擬此門禁」。**不做 UID spoof 破解。** |
| **MIFARE Classic** | **機型依賴**（手機／手錶 ReaderMode；**僅在使用者自行提供金鑰**時讀 sector；不內建大規模 key cracking） | **可行**（UID＋類型；金鑰進加密 vault，不存明文於模型） | **不可**（多數機型）→ `PROTOCOL_UNSUPPORTED` / `unsupported_emulate` | MVP 可只記 UID＋type；不做預設金鑰字典攻擊自動化。詳情頁明示「僅備份」。 |
| **NDEF** | **可行**（手機或支援讀卡的手錶） | **可行**（payload；可進加密備份） | **可行**（優先路徑：手錶 Type 4 HCE 模擬 NDEF） | `CapabilityProbe` 通過且有 payload → `SUPPORTED`；`StrawHostApduService` 回應 Type 4 APDU。讀到 NDEF **≠** 門禁已開。 |
| **DESFire** | **可行（僅摘要）**：ATQA／SAK／ATS／應用目錄摘要 | **可行**（標記為不可克隆） | **不可** → `PROTOCOL_UNSUPPORTED` | **明確不可克隆**。文案：「DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。」 |

## Stock HCE 與 UID

| 陳述 | 真偽 |
|------|------|
| Stock Android／Wear HCE 可模擬任意 UID 並開門禁 | **假**（一般不可；產品禁止如此宣稱） |
| Stock HCE 可走 ISO-DEP／Type 4（如 NDEF）回應 | **真**（在 feature 與 AID 註冊成功時） |
| DESFire／多數加密門禁可被本 App「完整複製」 | **假**；僅偵測與標記 |
| Tile／Complication 點擊＝已開門 | **假**；僅開啟「準備模擬」畫面 |

## 能力探測原則

1. 檢查 `PackageManager.FEATURE_NFC` 與 `FEATURE_NFC_HOST_CARD_EMULATION`。
2. 確認 `HostApduService`（`StrawHostApduService`）註冊與 AID meta-data（NDEF Type 4）。
3. 僅在 probe 通過且卡類型為支援路徑（MVP：NDEF + payload）時將 `emulateStatus` 標為 `SUPPORTED`。
4. UID-only／Classic／DESFire：UI 顯示誠實狀態，保留「僅備份」說明；禁止「已開門」文案。

## 備份

| 項目 | 行為 |
|------|------|
| 格式 | `.strawnfc` = `SNFC` magic + AES-GCM（PBKDF2 密碼衍生） |
| 內容 | 卡片清單 JSON（不含 Classic 明金鑰） |
| 入口 | Wear `BackupScreen`；Mobile `BackupActivity`（SAF） |

## MVP 不做

- 未授權複製、金鑰暴力破解自動化
- 悠遊卡／信用卡／交通卡複製
- DESFire 克隆
- Root／Magisk 專用 UID spoof 教學或打包
- 假開門／「已解鎖門禁」文案
