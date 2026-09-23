# Kế hoạch thiết kế lại màn hình chọn màu

## Mục tiêu
- Cho phép chọn phím trực tiếp bằng cách chạm vào cụm phím preview, có trạng thái chọn rõ ràng.
- Preview mô phỏng đúng bố cục overlay: cụm phím ở khu vực phía trên, KPS/Total bên dưới và trail chạy phía sau/phía dưới phím.
- Preview chiếm khoảng 1/3 chiều cao màn hình; khu vực chỉnh màu chiếm phần còn lại, dễ quét và phân biệt các nhóm thuộc tính.
- Giữ nguyên cơ chế lưu màu cơ bản/nâng cao và tương thích với `ThemeColorStore`.

## Các bước thực hiện
1. Cập nhật layout `activity_theme_editor.xml`: chia màn hình theo chiều dọc thành preview khoảng 1/3 và vùng chỉnh sửa cuộn ở phần còn lại; bố trí header/tab/target selector rõ ràng.
2. Viết lại `ThemePreviewView`: vẽ cụm phím theo tỉ lệ và kiểu overlay, bổ sung trail preview, xử lý chạm để chọn phím, hiển thị phím đang chọn và callback về Activity.
3. Cập nhật `ThemeEditorActivity`: đồng bộ target nâng cao với phím được chạm, hiển thị target selector trực quan, tránh rebuild làm mất lựa chọn, nhóm các màu theo Normal/Pressed/Trail và refresh preview ngay khi thay đổi.
4. Cải thiện dialog màu để các thanh RGBA và giá trị dễ hiểu, cập nhật preview màu trong dialog nếu cần.
5. Build/kiểm tra lỗi phân tích, triển khai và kiểm tra UI trên thiết bị; sửa các lỗi phát sinh.

## Kiểm chứng
- Build debug thành công.
- Màn hình preview nhận chạm vào từng phím và đổi target đúng.
- Chỉnh màu nền/chữ/viền/trail của phím đang chọn được phản ánh ngay.
- Trail hiển thị khi preview tự chạy hoặc khi chạm phím.
