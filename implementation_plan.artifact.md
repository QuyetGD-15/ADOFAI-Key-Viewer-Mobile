# Sửa overlay bị giảm opacity toàn cửa sổ

## Bằng chứng và nguyên nhân
- OverlayService.kt:1064–1073 tạo cửa sổ toàn màn hình TYPE_APPLICATION_OVERLAY + FLAG_NOT_TOUCHABLE.
- dumpsys window trên thiết bị hiện tại xác nhận cửa sổ com.quyetgd.keyvieweroverlay: alpha=0.8; mShownAlpha=0.8 mAlpha=0.8 mLastAlpha=0.8.
- Đây là giảm opacity ở cấp cửa sổ, không phải màu theme. Android 12+ có hạn chế thao tác xuyên cửa sổ không đáng tin cậy; accessibility overlay thuộc nhóm trusted windows.

## Ràng buộc
- Không chỉnh ThemeColorStore, mã màu, preset hay SharedPreferences để ép alpha màu lên 100%.
- Không dùng tăng alpha màu, vẽ chồng để bù opacity, API ẩn hay vô hiệu hóa bảo vệ chạm toàn hệ thống.
- Giữ alpha riêng của từng thành phần, nền trống và hiệu ứng theme.

## Thực hiện (chờ người dùng duyệt)
1. Đưa việc attach/update/remove cửa sổ KeyViewer qua WindowManager của TouchRendererService với TYPE_ACCESSIBILITY_OVERLAY. OverlayService tiếp tục quản lý nội dung, theme và input. Quản lý kết nối/ngắt service, xoay màn hình, thay key mode và giải phóng cửa sổ; không chỉ đổi type trên WindowManager của foreground service.
2. Cập nhật kiểm tra quyền và hướng dẫn setup/UI: cần bật Trợ năng để hiển thị KeyViewer đúng opacity, kể cả input chạm/Shizuku. Khi service chưa kết nối, không âm thầm dùng lại cửa sổ bị giảm opacity; thông báo trạng thái rõ ràng. Không tự bật quyền.
3. Bỏ phép ghi đè paint.alpha = 255 hiện có tại KeyTrailView.drawSingleTrail để tôn trọng alpha màu rain. Đây là lỗi riêng làm mất alpha thiết kế, không phải nguyên nhân giảm opacity toàn overlay. Giữ nguyên thiết kế glow/shadow.
4. Build và kiểm thử hồi quy: cửa sổ thuộc accessibility service, thao tác xuyên tới game, màu opaque/semitransparent/transparent, rain, xoay màn hình, bật/tắt overlay và ngắt/kết nối Trợ năng. Xác minh dumpsys không còn hệ số alpha 0.8 trên cửa sổ KeyViewer; báo rõ kiểm tra nào không thực hiện được.

## Tác động cần duyệt
Chế độ chạm hiện chỉ cần Shizuku cho input; sau sửa sẽ cần thêm Trợ năng cho cửa sổ hiển thị. Không thay đổi nguồn input thành accessibility và không thay đổi dữ liệu theme.
