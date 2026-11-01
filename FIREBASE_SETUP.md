# Kết nối Firebase cho SmartSplit

App dùng Firebase Authentication và Cloud Firestore để đăng nhập, đồng bộ dữ liệu và ảnh. Firebase Storage chỉ còn được dùng để thử hiển thị ảnh cũ đã lưu bằng Storage; không cần tạo bucket cho ảnh mới. Nếu chưa thêm cấu hình Firebase, app sẽ báo lỗi thay vì giả vờ đồng bộ.

## 1. Tạo Firebase project và thêm Android app

1. Mở [Firebase Console](https://console.firebase.google.com/) và tạo project.
2. Thêm Android app với package name chính xác: `com.smartsplit.app`.
3. Tải `google-services.json` và đặt tại `app/google-services.json` (cùng cấp với `app/build.gradle.kts`).
4. Không commit file cấu hình này nếu repository của bạn không được phép chứa cấu hình Firebase.

Gradle chỉ bật Google Services plugin khi tìm thấy file này; các Firebase SDK đã được khai báo sẵn trong app.

## 2. Bật đăng nhập email/mật khẩu

Trong Firebase Console, vào **Authentication → Sign-in method**, bật **Email/Password**.

Tài khoản app dùng Firebase Authentication. Mật khẩu không được lưu vào SQLite/Firestore. Đăng nhập bằng email, cùng một email và mật khẩu trên hai thiết bị.

## 3. Tạo cơ sở dữ liệu Firestore

Tạo Cloud Firestore database. Có thể bắt đầu ở production mode, sau đó xuất bản nội dung `firestore.rules` ở thư mục gốc repository trong **Firestore Database → Rules**.

Dữ liệu được lưu theo cấu trúc:

- `users/{uid}`: hồ sơ tài khoản.
- `users/{uid}/notifications/{notificationId}`: thông báo trong ứng dụng, đồng bộ giữa các máy.
- `usernames/{username}`: ánh xạ tên đăng nhập sang email để hỗ trợ đăng nhập bằng tên tài khoản.
- `projects/{projectId}`: dự án và danh sách thành viên.
- `projects/{projectId}/members/{uid}`: hồ sơ thành viên phục vụ giao diện.
- `projects/{projectId}/bills/{billId}`: bill, mặt hàng, khoản nợ, trạng thái thanh toán, bill gộp và chuyển khoản tối ưu.
- `projects/{projectId}/images/{imageId}`: ảnh bill đã nén, chỉ thành viên dự án được đọc.
- `users/{uid}/images/{imageId}`: ảnh QR nhận tiền đã nén, người dùng đã đăng nhập được đọc.
- `inviteCodes/{code}`: tra mã mời dự án.

## 4. Ảnh đồng bộ không cần Cloud Storage

Ảnh QR, hóa đơn gốc và minh chứng chuyển tiền được thu nhỏ tối đa 1600 px, nén JPEG và giới hạn còn khoảng 600 KB mỗi ảnh. Nội dung ảnh được lưu dưới dạng Base64 trong tài liệu Firestore để dùng được trên Spark mà không cần tạo Storage bucket hoặc bật Blaze.

Không cần xuất bản `storage.rules` cho luồng ảnh mới. Quy tắc Firestore `firestore.rules` cần có các quyền đọc/ghi cho hai đường dẫn `images` ở trên. Ảnh cũ có URL Firebase Storage vẫn được giữ nguyên; khả năng tải chúng phụ thuộc vào quyền truy cập Storage của project.

Firestore giới hạn mỗi tài liệu ở 1 MiB. Hạn mức miễn phí Spark được tính chung cho toàn project; nếu vượt hạn mức, thao tác có thể bị từ chối. Theo Firebase, mức miễn phí gồm 1 GiB lưu trữ, 20.000 lượt ghi/ngày, 50.000 lượt đọc/ngày và 10 GiB tải ra/tháng. Năm mươi ảnh ở mức giới hạn của app chiếm tối đa khoảng 40 MiB sau khi mã hóa Base64, chưa tính metadata.

## 5. Kiểm tra đồng bộ trên hai thiết bị

1. Cài cùng APK trên cả hai máy; cả hai cần mạng Internet.
2. Đăng ký/đăng nhập cùng một Firebase account.
3. Tạo dự án trên máy thứ nhất, sao chép mã mời và tham gia trên máy thứ hai.
4. Tạo/sửa bill, tải ảnh, xác nhận thanh toán hoặc gộp bill ở một máy; các thay đổi được nghe qua Firestore listener trên máy kia.

App cũng giữ SQLite làm cache cục bộ để hiển thị dữ liệu đã tải. Cloud Firestore là nguồn đồng bộ giữa các máy. Firestore có thể xếp hàng một số ghi khi offline; việc tải ảnh cần kết nối mạng.

## Chuyển dữ liệu từ bản cũ

Khi đăng nhập hoặc đăng ký Firebase bằng cùng email với tài khoản SQLite cũ trên thiết bị, app thử chuyển hồ sơ, dự án và bill cục bộ lên cloud một lần, đồng thời thay ID của tài khoản hiện tại bằng Firebase UID. Vì các tài khoản cũ và dữ liệu ở thiết bị khác không thể được suy ra từ một máy, mỗi thành viên cần đăng ký/đăng nhập Firebase rồi tham gia dự án bằng mã mời để bắt đầu đồng bộ trên tài khoản đó.

## Giới hạn backend hiện tại

- Dữ liệu dự án, bill, thành viên và ảnh có đồng bộ thời gian thực.
- Đăng nhập email/mật khẩu và tham gia dự án bằng mã mời dùng Firebase.
- Mã mời dự án gồm 8 ký tự ngẫu nhiên.
- Firebase Rules phải được cài đặt từ hai file trong repository trước khi cho người dùng khác truy cập.
