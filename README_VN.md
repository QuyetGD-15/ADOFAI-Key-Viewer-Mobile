# 🌍 Language / Ngôn ngữ
[Tiếng Việt](README_VN.md) | [English](README.md)

---
# 🎹 ADOFAI Key Viewer Mobile

Một công cụ Overlay siêu nhẹ, trễ thấp (Ultra-low latency) dành riêng cho game thủ ADOFAI và các tựa game nhịp điệu trên Android. Vượt qua giới hạn của Game Turbo để bắt trọn từng thao tác múa phím của bạn.

## ✨ Tính Năng Nổi Bật
### 🎮 Hai Chế Độ Đầu Vào Biến Thiên Độc Lập
Ứng dụng cô lập hoàn toàn mã nguồn thực thi giữa hai chế độ, đảm bảo không có tài nguyên thừa chạy lén:
* Chế độ Cảm ứng (Touch Mode):
  * Sử dụng lõi Shizuku Core đọc trực tiếp sự kiện phần cứng (getevent), loại bỏ hoàn toàn độ trễ của hệ điều hành.
  * Tích hợp thuật toán chống nhấn dính phím/nhấn nhầm (Anti-Misclick) thông minh dựa trên việc tính toán khoảng cách hình học đến tâm hitbox O(1).
* Chế độ Bàn phím Vật lý (Physical Keyboard Mode):
  * Dành riêng cho bàn phím cơ kết nối qua cổng OTG hoặc Bluetooth.
  * Truyền dữ liệu tĩnh trực tiếp vào RAM (Singleton Instance Access) thông qua Accessibility Service cho tốc độ phản hồi 0ms.

### 📊 Đa Dạng Chế Độ Phím (Isolated Key Modes)
* Mở rộng không gian với đầy đủ các cấu hình layout: 4 KEY, 6 KEY, 8 KEY, và 10 KEY.

## 📥 Cài Đặt
1. Tải file apk mới nhất tại mục [Releases](https://github.com/QuyetGD-15/ADOFAI-Key-Viewer-Mobile/releases).
2. Cài đặt ứng dụng.

---
**Developed with 💖 by [quyetgd]**
