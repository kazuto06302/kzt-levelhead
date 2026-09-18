# KZT-LevelHead

> **Hypixel Network 向け LevelHead MOD**
>
> プレイヤーの各種ステータスを頭上に表示し、ランク・スターカラー・プレフィックスなどを自由にカスタマイズできます。

**Forge / DawnClient · Minecraft 1.8.9**

---

## ✨ Features

### 📊 Player Stats

Hypixel のプレイヤー情報を取得し、頭上に表示します。

| Stat | Available |
| --- | :---: |
| Network Level | ✅ |
| BedWars Stars | ✅ |
| SkyWars Stars | ✅ |
| UHC Level | ✅ |

### 🎨 Display Customization

- **Rank の同時表示**
  - V
  - V+
  - M
  - M+
  - ++
  - NI
  - YT
- **BedWars / SkyWars スターのカラー化**
- **カスタムプレフィックス**
- 完全にカスタマイズ可能な見た目を目指して開発中

### ⚡ API & Performance

- **Hypixel Official API** / **Custom API** に対応
- **Seraph との API 競合対策**
- 標準 **6時間キャッシュ** による重複リクエストの削減
- API Rate Limit 対策
- **In-Game のプレイヤーのみ取得**
  - ロビーにいるプレイヤーなど、不要な API リクエストを抑制

---

## 🌐 API

Seraph との API 競合を避けるため、**Custom Hypixel Proxy** を利用した API のセルフホスト構成に対応しています。

### Custom API

取得した Hypixel API の結果を **Port 3015** でそのまま提供します。

### Cache

取得したプレイヤー情報は標準で **6時間** キャッシュされます。

これにより、

- 同じプレイヤーへの重複リクエスト
- 不要な API リクエスト
- API Rate Limit の消費

を抑えます。

---

## 🛠 Commands

すべてのコマンドは `/levelhead` から使用できます。

| Command | Description |
| --- | --- |
| `/levelhead key <key>` | Hypixel API Key を設定 |
| `/levelhead mode <hypixel\|custom>` | API モードを変更 |
| `/levelhead api <url>` | Custom API URL を設定 |
| `/levelhead clearcache` | キャッシュを削除 |
| `/levelhead reload` | 設定を再読み込み |
| `/levelhead interval` | リクエスト間隔を設定 |
| `/levelhead game` | 対象ゲームモードを設定 |

---

## 🎮 Supported Environment

- **Minecraft:** 1.8.9
- **Mod Loader:** Forge
- **Client:** DawnClient
- **Network:** Hypixel

---

## 📌 Roadmap

- [ ] スターブースター
- [ ] より細かい表示カスタマイズ
- [ ] その他の Hypixel Stats 対応

---

## 📫 Contact

**Developer:** Kazut0_  

---

<p align="center">
  <sub>KZT-LevelHead — A customizable LevelHead mod for Hypixel Network.</sub>
</p>
