# KZT-LevelHead

> **A LevelHead mod for the Hypixel Network**
>
> Displays player statistics above their heads with customizable ranks, star colors, prefixes, and more.

**Forge / DawnClient · Minecraft 1.8.9**

---

## ✨ Features

### 📊 Player Stats

Fetch and display Hypixel player statistics above their heads.

| Stat | Available |
| --- | :---: |
| Network Level | ✅ |
| BedWars Stars | ✅ |
| SkyWars Stars | ✅ |
| UHC Level | ✅ |

### 🎨 Display Customization

- **Rank display**
  - V
  - V+
  - M
  - M+
  - ++
  - NI
  - YT
- **Colored BedWars / SkyWars stars**
- **Custom prefixes**
- Designed to provide a highly customizable appearance

### ⚡ API & Performance

- Supports the **Official Hypixel API** and **Custom API**
- **Seraph API conflict prevention**
- Standard **6-hour cache** to reduce duplicate requests
- Helps prevent unnecessary API rate-limit usage
- Only fetches players when they are **in-game**
  - Avoids unnecessary requests for lobby players

---

## 🌐 API

To avoid API conflicts with Seraph, KZT-LevelHead supports a self-hosted API setup using **Custom Hypixel Proxy**.

### Custom API

Hypixel API responses can be served directly through **Port 3015**.

### Cache

Player information is cached for **6 hours** by default.

This helps reduce:

- Duplicate requests for the same player
- Unnecessary API requests
- API rate-limit usage

---

## 🛠 Commands

All commands are available through `/levelhead`.

| Command | Description |
| --- | --- |
| `/levelhead key <key>` | Set the Hypixel API key |
| `/levelhead mode <hypixel\|custom>` | Change the API mode |
| `/levelhead api <url>` | Set the Custom API URL |
| `/levelhead clearcache` | Clear the cache |
| `/levelhead reload` | Reload the configuration |
| `/levelhead interval` | Set the request interval |
| `/levelhead game` | Set the target game mode |

---

## 🎮 Supported Environment

- **Minecraft:** 1.8.9
- **Mod Loader:** Forge
- **Client:** DawnClient
- **Network:** Hypixel

---

## 📌 Roadmap

- [ ] Star Booster
- [ ] More display customization options
- [ ] Additional Hypixel statistics

---

## 📫 Contact

**Developer:** Kazut0_

---

<p align="center">
  <sub>KZT-LevelHead — A customizable LevelHead mod for the Hypixel Network.</sub>
</p>
