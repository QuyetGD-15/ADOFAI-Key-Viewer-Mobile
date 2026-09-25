# Kế hoạch cải tạo màn hình cài đặt Hitbox

## Mục tiêu
Đánh giá màn hình cấu hình Hitbox trên thiết bị thật, sau đó cải thiện trực quan và UX/UI mà không làm thay đổi cơ chế lưu/tính toán tọa độ hiện có.

## Phạm vi
1. Kiểm tra UI hiện tại trên emulator/thiết bị và luồng thao tác kéo/đổi kích thước.
2. Cải thiện bố cục thanh công cụ, hướng dẫn thao tác, màu sắc/độ tương phản và khả năng chạm.
3. Cải thiện HitboxView để dễ phân biệt vùng đang chỉnh sửa, tay nắm và trạng thái tương tác.
4. Bổ sung các xử lý UX an toàn nếu cần (xác nhận reset, phản hồi trực quan), giữ tương thích đa ngôn ngữ ở mức tài nguyên hiện có.
5. Build và kiểm tra lại màn hình sau thay đổi.

## Tiêu chí hoàn thành
- Màn hình rõ ràng trên nền game/overlay, nút thao tác không che vùng chỉnh sửa quá mức.
- Hướng dẫn ngắn gọn, dễ hiểu; các nút có vùng chạm phù hợp.
- Vùng hitbox và tay nắm có tương phản tốt, thao tác kéo/resize vẫn mượt.
- Build debug thành công và kiểm tra thiết bị không có lỗi runtime rõ ràng.
