# Kế hoạch tối ưu hiệu năng toàn dự án

## Mục tiêu
- Giảm CPU, GPU và RAM trên toàn ứng dụng.
- Ưu tiên cao nhất cho chế độ overlay: giảm tần suất render/cập nhật, tránh tạo object và allocation trong vòng lặp, hạn chế wake-up và xử lý khi không có thay đổi.
- Giữ nguyên hành vi và giao diện cần thiết.

## Phạm vi rà soát
1. Kiểm kê module, build configuration, manifest, dependency và toàn bộ mã nguồn/resource liên quan đến overlay.
2. Phân tích đường chạy overlay, lifecycle/service, vòng lặp cập nhật, custom View/Compose, animation, bitmap và IPC.
3. Rà soát các màn hình/cơ chế ngoài overlay để loại bỏ allocation, observer, coroutine hoặc recomposition không cần thiết.
4. Thực hiện các thay đổi có bằng chứng từ mã nguồn; tránh tối ưu mù làm thay đổi chức năng.
5. Build, kiểm tra inspection và xác minh luồng overlay trên thiết bị nếu có thể.

## Chiến lược kỹ thuật dự kiến
- Chỉ redraw khi state hình ảnh thực sự thay đổi; coalesce/batch các update.
- Tách input/event processing khỏi render; dùng frame callback hoặc scheduler phù hợp thay vì timer/wake-up quá dày.
- Reuse buffer/object/paint/path và cache dữ liệu bất biến; tránh cấp phát trong onDraw/update loop.
- Giảm overdraw, layer/elevation/blur/alpha không cần thiết; tắt animation khi overlay không thay đổi.
- Thu hẹp phạm vi observer/coroutine, hủy công việc theo lifecycle, tránh leak context/service.
- Tối ưu cấu hình release/R8 và resource nếu phù hợp, không hy sinh khả năng debug hoặc tính đúng đắn.

## Xác minh
- Chạy inspection trên các file đã sửa.
- Chạy Gradle build/test phù hợp.
- Kiểm tra triển khai và luồng overlay trên thiết bị; đọc logcat khi cần.
- Báo cáo rõ thay đổi, giới hạn và những điểm cần đo bằng profiler thực tế.
