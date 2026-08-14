# StrawNFC 能力矩陣

> 產品誠實聲明：**Stock HCE ≠ 任意 UID 門禁克隆**。本表定義 MVP 行為；「可行」指協定／常見硬體路徑，「不可」為產品明確拒絕或技術上不支援，「機型依賴」須經能力探測後才啟用。

政策：`own_only`（僅自己擁有／已授權的卡）。詳見 [`legal-scope.md`](legal-scope.md)。

## 矩陣（讀取 × 儲存 × 模擬）

| 卡類型 | 讀取 | 儲存 | 模擬（HCE／模擬使用） | 說明 |
|--------|------|------|------------------------|------|
| **UID-only** | **可行**（手機 NFC `Tag.id`；手錶可手動輸入） | **可行** | **機型依賴** | 可備份 UID。Stock Wear Host Card Emulation **通常無法改寫對外 UID**；多數 UID 門禁讀器驗的是 UID，因此**不能宣稱任意門禁可開**。`FEATURE_NFC_HOST_CARD_EMULATION` 不足或 UID spoof 不可用 → UI：「此裝置無法模擬此門禁」。 |
| **MIFARE Classic** | **機型依賴**（手機；**僅在使用者自行提供金鑰**時讀 sector；不內建大規模 key cracking） | **可行**（UID＋類型；金鑰進加密 vault，不存明文於模型） | **不可**（多數機型）→ `unsupported_emulate` / `PROTOCOL_UNSUPPORTED` 或 `DEVICE_UNSUPPORTED` | MVP 可只記 UID＋type；不做預設金鑰字典攻擊自動化。 |
| **NDEF** | **可行**（手機讀寫） | **可行**（payload） | **可行**（優先路徑：手錶 Type 4 HCE 模擬 NDEF） | 能力探測通過後啟用；無硬體時以 APDU 單元測試驗證。 |
| **DESFire** | **可行（僅摘要）**：ATQA／SAK／ATS／應用目錄摘要 | **可行**（標記為不可克隆） | **不可** → `PROTOCOL_UNSUPPORTED` | **明確不可克隆**。加密門禁；StrawNFC 只備份識別資訊，無法也不應克隆。 |

## Stock HCE 與 UID

| 陳述 | 真偽 |
|------|------|
| Stock Android／Wear HCE 可模擬任意 UID 並開門禁 | **假**（一般不可；產品禁止如此宣稱） |
| Stock HCE 可走 ISO-DEP／Type 4（如 NDEF）回應 | **真**（在 feature 與 AID 註冊成功時） |
| DESFire／多數加密門禁可被本 App「完整複製」 | **假**；僅偵測與標記 |

## 能力探測原則

1. 檢查 `PackageManager.FEATURE_NFC_HOST_CARD_EMULATION`（及相關 NFC feature）。
2. 確認 `HostApduService` 註冊與 AID meta-data。
3. 僅在 probe 通過且卡類型為支援路徑（MVP：NDEF）時將 `emulateStatus` 標為 `SUPPORTED`。
4. UID-only／Classic／DESFire：UI 顯示誠實狀態，保留「僅備份」說明。

## MVP 不做

- 未授權複製、金鑰暴力破解自動化
- 悠遊卡／信用卡／交通卡複製
- DESFire 克隆
- Root／Magisk 專用 UID spoof 教學或打包
