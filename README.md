# 🌍 Language / Ngôn ngữ
[Tiếng Việt](README_VN.md) | [English](README.md)

---

# 🎹 ADOFAI Key Viewer Mobile

An ultra-lightweight, ultra-low latency overlay tool designed specifically for ADOFAI and other rhythm games on Android. Bypass Game Turbo limitations to capture every single keypress.

## ✨ Key Features
### 🎮 Dual Independent Input Modes
The application completely isolates the execution code between the two modes, ensuring no background resources are wasted:
* Touch Mode:
  * Utilizes Shizuku Core to directly read hardware events (getevent), completely eliminating OS-level delays.
  * Integrates a smart Anti-Misclick algorithm based on O(1) geometric distance calculation to the hitbox center.
* Physical Keyboard Mode:
  * Specially designed for mechanical keyboards connected via OTG or Bluetooth.
  * Transmits static data directly into RAM (Singleton Instance Access) via Accessibility Service for a true 0ms response time.

### 📊 Diverse Key Modes (Isolated Key Modes)
* Expands your workspace with full layout configurations: 4 KEY, 6 KEY, 8 KEY, and 10 KEY.

* **Minimalist UI:** The Drop Shadow design ensures the keyrain always stands out, even against bright white Flashbang maps.

<img width="400" height="379" alt="1000219385" src="https://github.com/user-attachments/assets/9f9c4f22-54eb-425d-a469-d5b8afc87c2d" />


## 📥 Installation
1. Download the latest APK file from the [Releases](https://github.com/QuyetGD-15/ADOFAI-Key-Viewer-Mobile/releases) page.
2. Install the application.

---
**Developed with 💖 by [quyetgd]**
