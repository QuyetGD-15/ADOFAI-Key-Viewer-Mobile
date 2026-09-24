# Kế hoạch rà soát và tối ưu RAM

## Mục tiêu
Giảm mức sử dụng RAM và tránh các nguồn gây rò rỉ/bộ nhớ tăng không cần thiết, nhưng không làm thay đổi hành vi sản phẩm ngoài phạm vi tối ưu hiệu năng.

## Phạm vi rà soát
1. Kiểm tra cấu trúc module, cấu hình build và manifest để tìm cấu hình debug/release, tài nguyên dư thừa và tuỳ chọn đóng gói ảnh hưởng RAM.
2. Rà soát Activity/Fragment/View/ViewModel/adapter/listener/coroutine để phát hiện lifecycle leak, giữ tham chiếu Context/View, cache không giới hạn và cập nhật UI quá thường xuyên.
3. Rà soát layout và drawable (đặc biệt `activity_main.xml`) để phát hiện view lồng sâu, ảnh kích thước lớn, bitmap không cần thiết và thành phần có thể thay bằng cấu hình nhẹ hơn.
4. Rà soát dependency và cách tải tài nguyên/dữ liệu; chỉ thay đổi khi có bằng chứng rõ ràng và tương thích.
5. Áp dụng các cải thiện an toàn, sau đó build/kiểm tra static analysis và xác nhận ứng dụng vẫn chạy.

## Nguyên tắc
- Ưu tiên thay đổi nhỏ, đo được và không phá chức năng.
- Không xoá thành phần chỉ vì “có vẻ dư” nếu chưa xác nhận usage.
- Không tối ưu mù bằng cách tắt tính năng hoặc giảm chất lượng hiển thị ngoài yêu cầu.
- Báo cáo rõ những rủi ro còn lại và đề xuất profiling bằng Android Studio/Perfetto nếu cần số liệu runtime.

## Xác minh
- Chạy kiểm tra phân tích file sau khi sửa.
- Build module ứng dụng bằng Gradle.
- Nếu thiết bị khả dụng, triển khai và kiểm tra nhanh màn hình chính/lifecycle.
