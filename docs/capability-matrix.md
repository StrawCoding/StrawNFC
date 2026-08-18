# StrawNFC 能力矩陣

> 產品誠實聲明：**Stock HCE ≠ 任意 UID 門禁克隆**。本表定義 MVP 行為；「可行」指協定／常見硬體路徑，「不可」為產品明確拒絕或技術上不支援，「機型依賴」須經能力探測後才啟用。

政策：`own_only`（僅自己擁有／已授權的卡）。詳見 [`legal-scope.md`](legal-scope.md)。

**SNFC3 實作狀態（Backup＋Tile＋誠實 HCE）：** `CapabilityProbe` + `StrawHostApduService`（NDEF Type 4 AID `D2760000850101`）已落地；UID／Classic／DESFire 在 Emulate UI **誠實標示不可／機型依賴**，永不宣稱「已開門」。

## 三種狀態（不要把「有 NDEF」當成「可 HCE」）

| 狀態 | 意思 | UI |
|---|---|---|
| **① 可備份** | 已讀取 UID／類型／payload，但不能對 Reader 重現協定 | 「僅備份 — 無法模擬」 |
| **② HCE 可走 ISO-DEP** | 裝置有 `nfc.hce`，`HostApduService` 能收 APDU。**不是**任意門禁協議重放；產品不做未知 ISO-DEP／DESFire 克隆 | 不提供「開始模擬」 |
| **③ Type 4 NDEF 可模擬** | ①＋②＋非空 NDEF payload＋完整 Type 4 APDU（SELECT AID → CC E103 → NDEF File E104） | 「可進行 Type 4 NDEF 模擬」→「開始模擬」 |

Android **不會**因為手上有 `NdefMessage` 就自動變成 NFC Forum Type 4 Tag。Reader 先 SELECT AID，系統才把 APDU 路由進 Service。

模擬中若 APDU 紀錄空白 → routing／AID；若有 `00 A4` / `00 B0` 但手機仍不認 NDEF → Type 4 狀態機。`adb logcat -s StrawNFC-HCE`

**SNFC4 驗收狀態（CI＋手動清單）：** `.github/workflows/android-ci.yml`（`:shared:test` + `:wear:testDebugUnitTest` + `:mobile:testDebugUnitTest` + Wear／Mobile `assembleDebug`）；手動步驟見 [`manual-test-checklist.md`](manual-test-checklist.md)。Debug APK：`wear/build/outputs/apk/debug/wear-debug.apk`、`mobile/build/outputs/apk/debug/mobile-debug.apk`。證據：`tests/strawnfc/output/snfc4-ci-acceptance.json`。**無假開門宣稱**；最終 PASS 僅 Hermes verify + OpenCode APPROVE。

**SNFC5（Play 上架管線）：** [`docs/play-release.md`](play-release.md)。商店上架**不放寬**本矩陣；internal track 發布仍是備份＋誠實模擬，不是「任意門禁可開」。

## 矩陣（讀取 × 儲存 × 模擬）

| 卡類型 | 讀取 | 儲存 | 模擬（HCE／模擬使用） | 說明 |
|--------|------|------|------------------------|------|
| **UID-only** | **可行**（手機或手錶 NFC `Tag.id`；無讀卡硬體時可手動輸入） | **可行**（含加密 `.strawnfc` 備份） | **機型依賴 → MVP 標 `DEVICE_UNSUPPORTED`** | 可備份 UID。Stock Wear Host Card Emulation **通常無法改寫對外 UID**；多數 UID 門禁讀器驗的是 UID，因此**不能宣稱任意門禁可開**。UI：「此裝置無法模擬此門禁」。**不做 UID spoof 破解。** |
| **MIFARE Classic** | **機型依賴**（手機／手錶 ReaderMode；**僅在使用者自行提供金鑰**時讀 sector；不內建大規模 key cracking） | **可行**（UID＋類型；金鑰進加密 vault，不存明文於模型） | **不可**（多數機型）→ `PROTOCOL_UNSUPPORTED` / `unsupported_emulate` | MVP 可只記 UID＋type；不做預設金鑰字典攻擊自動化。詳情頁明示「僅備份」。 |
| **NDEF** | **可行**（手機或支援讀卡的手錶） | **可行**（payload；可進加密備份） | **僅 Type 4 Tag 路徑** | 需 payload 可解碼且 ≤ 2048 bytes（`Type4Payload`），**且** probe（HCE＋service）通過才 `SUPPORTED`。模擬的是 NFC Forum Type 4 APDU，不是「讀到 NDEF 就自動變成標籤」。讀到 NDEF **≠** 門禁已開。**注意分類優先序：** 原卡若同時有 `IsoDep`（真 Type 4 卡）會被歸為 DESFire＝僅備份；只有無 `IsoDep` 的 Type 2（NTAG／Ultralight）NDEF 會進入本模擬路徑。 |
| **DESFire** | **可行（僅摘要）**：ATQA／SAK／ATS／應用目錄摘要 | **可行**（標記為不可克隆） | **不可** → `PROTOCOL_UNSUPPORTED` | **明確不可克隆**。文案：「DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。」 |

## Stock HCE 與 UID

| 陳述 | 真偽 |
|------|------|
| Stock Android／Wear HCE 可模擬任意 UID 並開門禁 | **假**（一般不可；產品禁止如此宣稱） |
| Stock HCE 可走 ISO-DEP／Type 4（如 NDEF）回應 | **真**（須 AID 路由到已實作的 Type 4 狀態機；有 NDEF payload 不夠） |
| DESFire／多數加密門禁可被本 App「完整複製」 | **假**；僅偵測與標記 |
| Tile／Complication 點擊＝已開門 | **假**；僅開啟「準備模擬」畫面 |

## 能力探測原則

1. 檢查 `PackageManager.FEATURE_NFC` 與 `FEATURE_NFC_HOST_CARD_EMULATION`。
2. 確認 `HostApduService`（`StrawHostApduService`）註冊與 AID meta-data（NDEF Type 4）。
3. 僅在 probe 通過且卡類型為支援路徑（MVP：NDEF + payload 可組成 Type 4 檔案）時將 `emulateStatus` 標為 `SUPPORTED`。
4. UID-only／Classic／DESFire：UI 顯示誠實狀態，保留「僅備份」說明；禁止「已開門」文案。
5. **NFC 工作階段控制（Wear）：** `NfcModeController` 保證 ReaderMode 與 HCE **互斥**；模擬時呼叫 `CardEmulation.setPreferredService`；NFC 關閉時導向系統設定（App 不能強制開無線電）。
6. **工作階段必須即時失效：** 「停止模擬」或換卡會 bump session epoch，`StrawHostApduService` 依 epoch 丟棄快取 handler，`onDeactivated` 另清除選檔狀態——已停止的階段**不得**再回應 Reader，換卡**不得**回應舊卡 payload。

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
