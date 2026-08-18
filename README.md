# StrawNFC

Wear OS 為主的 NFC／門禁卡**備份與清單**工具；僅服務自己擁有或已獲授權的卡片（`own_only`）。

| 項目 | 值 |
|------|-----|
| Package | `xyz.wastebase.strawnfc` |
| 模組 | `:shared` · `:mobile`（薄 Companion）· `:wear`（主 UI） |
| 版本 | `VERSION`（a.b.c.d），改碼後執行 `bash scripts/bump-version.sh` |
| 分支 | 唯一 `main` |

## 產品邊界（必讀）

- **own_only：** 首次啟動須同意僅儲存／模擬自己擁有或已授權的卡。
- **不做：** 未授權門禁破解、預設金鑰字典攻擊、交通／支付卡複製、DESFire 克隆。
- **Stock HCE ≠ 任意 UID 門禁克隆：** 多數 Wear 裝置的 Host Card Emulation **無法改寫對外 UID**。UID-only 門禁在本產品以「備份＋能力探測」呈現；若裝置無法模擬，UI 必須顯示「此裝置無法模擬此門禁」，**不得宣稱任意門禁可開**。

完整矩陣見 [`docs/capability-matrix.md`](docs/capability-matrix.md)；法律範圍見 [`docs/legal-scope.md`](docs/legal-scope.md)。

## 架構

| 模組 | 職責 |
|------|------|
| `:wear` | 主 UI：同意條款、卡片庫、**本機 NFC 讀卡（機型依賴）**、Tile／Complication、HCE（能力探測後） |
| `:mobile` | 極薄 Companion：手機 NFC 掃描 → Wear Data Layer 同步（保留） |
| `:shared` | 共用模型／序列化／備份編解碼／`NfcCardReader` |

手錶可獨立貼卡讀取（硬體支援時）；亦可手動 UID；手機掃描寫入仍保留。

## 建置

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :wear:assembleDebug :mobile:assembleDebug
```

Play 內部測試上架（signing → signed AAB → Fastlane `internal_testing` → `v*` tag workflow）見 [`docs/play-release.md`](docs/play-release.md)。上架不改變 `own_only` 與能力矩陣。

## 能力摘要

| 類型 | 讀取 | 儲存 | 模擬 |
|------|------|------|------|
| UID-only | 手機／手動 | 是 | 機型依賴；Stock HCE 通常不可改 UID |
| MIFARE Classic | 手機（使用者自備金鑰） | 是 | 多數不可 → unsupported |
| NDEF | 手機／手錶 | 是 | Type 4 HCE（僅 Type 2 來源、payload ≤ 2048 bytes） |
| DESFire | 僅識別摘要 | 是（標記） | **不可克隆** |
