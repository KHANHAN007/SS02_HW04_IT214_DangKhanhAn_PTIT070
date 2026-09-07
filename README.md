# Bài tập 4: Phân tích và tái thiết kế tầng dữ liệu theo Database-per-service

## 1. Bối cảnh hệ thống LibraX

LibraX đã được tách thành 4 service độc lập:

- `book-service`
- `member-service`
- `borrowing-service`
- `notification-service`

Tuy nhiên, cả 4 service vẫn cùng truy cập một database MySQL duy nhất là `librax_db`. Ngoài ra, `borrowing-service` còn thực hiện câu truy vấn JOIN trực tiếp sang bảng thuộc service khác:

```java
String sql = "SELECT b.title, m.name FROM borrowings br " +
        "JOIN books b ON br.book_id = b.id " +
        "JOIN members m ON br.member_id = m.id " +
        "WHERE br.id = ?";
```

Đây là dấu hiệu hệ thống mới chỉ tách code theo service, nhưng tầng dữ liệu vẫn còn coupling giống monolithic.

## 2. Các rủi ro coupling dữ liệu hiện tại

### 2.1 Vi phạm quyền sở hữu bảng dữ liệu

Trong mô hình Database-per-service, mỗi service chỉ được sở hữu và truy cập trực tiếp database của chính nó. Đoạn code hiện tại vi phạm nguyên tắc này ở các điểm sau:

| Bảng | Service sở hữu đúng | Service đang truy cập sai | Mức độ vi phạm |
|---|---|---|---|
| `books` | `book-service` | `borrowing-service` | `borrowing-service` đọc trực tiếp dữ liệu sách |
| `members` | `member-service` | `borrowing-service` | `borrowing-service` đọc trực tiếp dữ liệu thành viên |
| `borrowings` | `borrowing-service` | Đúng service | Không vi phạm |

`borrowing-service` chỉ nên lưu các thông tin thuộc nghiệp vụ mượn sách, ví dụ `borrowing_id`, `book_id`, `member_id`, `borrowed_at`, `returned_at`, `status`. Thông tin chi tiết như `book.title` và `member.name` phải được lấy thông qua API của service sở hữu dữ liệu.

### 2.2 Coupling schema giữa các service

`borrowing-service` đang phụ thuộc trực tiếp vào cấu trúc bảng `books` và `members`. Nếu `book-service` đổi tên cột `title` thành `book_title`, hoặc `member-service` tách `name` thành `first_name` và `last_name`, `borrowing-service` sẽ bị lỗi dù nghiệp vụ mượn sách không thay đổi.

Điều này làm mất tính độc lập khi phát triển và triển khai từng service.

### 2.3 Không thể triển khai database riêng

Vì câu SQL JOIN cần cả 3 bảng cùng nằm trong một database, nên hệ thống bị khóa vào kiến trúc `librax_db` dùng chung. Khi muốn tách thành `books_db`, `members_db`, `borrowings_db`, câu JOIN này sẽ không còn chạy được.

### 2.4 Tăng rủi ro lỗi dây chuyền

Một thay đổi nhỏ ở bảng `books` hoặc `members` có thể làm hỏng chức năng chi tiết phiếu mượn. Lỗi không nằm trong code của `borrowing-service`, nhưng lại làm `borrowing-service` bị ảnh hưởng. Đây là coupling ẩn rất nguy hiểm trong microservice.

### 2.5 Khó phân quyền và bảo mật dữ liệu

Khi mọi service cùng truy cập `librax_db`, rất khó giới hạn quyền đọc/ghi đúng phạm vi. Ví dụ `borrowing-service` chỉ cần biết `book_id` và `member_id`, nhưng nếu có quyền truy cập database chung, nó có thể đọc nhiều thông tin hơn mức cần thiết.

## 3. Đề xuất tách database theo Database-per-service

Thay vì dùng chung `librax_db`, hệ thống nên được tách thành 4 database độc lập:

| Service | Database riêng | Bảng chính | Quyền truy cập |
|---|---|---|---|
| `book-service` | `books_db` | `books`, `authors`, `categories` | Chỉ `book-service` truy cập trực tiếp |
| `member-service` | `members_db` | `members`, `member_profiles` | Chỉ `member-service` truy cập trực tiếp |
| `borrowing-service` | `borrowings_db` | `borrowings`, `borrowing_items` | Chỉ `borrowing-service` truy cập trực tiếp |
| `notification-service` | `notifications_db` | `notifications`, `notification_logs` | Chỉ `notification-service` truy cập trực tiếp |

Sơ đồ đề xuất:

```mermaid
flowchart LR
    Client[Client] --> BorrAPI[borrowing-service]

    BorrAPI --> BorrDB[(borrowings_db)]
    BorrAPI -->|REST: GET /api/books/{id}| BookAPI[book-service]
    BorrAPI -->|REST: GET /api/members/{id}| MemberAPI[member-service]
    BorrAPI -->|Event hoặc REST| NotiAPI[notification-service]

    BookAPI --> BookDB[(books_db)]
    MemberAPI --> MemberDB[(members_db)]
    NotiAPI --> NotiDB[(notifications_db)]
```

Sau khi tách database, service không được JOIN trực tiếp sang bảng của service khác. Giao tiếp giữa các service phải đi qua API hoặc event.

## 4. Thiết kế API thay thế JOIN

`book-service` cung cấp API:

```http
GET /api/books/{bookId}
```

Response:

```json
{
  "id": 1,
  "title": "Clean Architecture"
}
```

`member-service` cung cấp API:

```http
GET /api/members/{memberId}
```

Response:

```json
{
  "id": 10,
  "name": "Nguyen Van A"
}
```

`borrowing-service` chỉ truy vấn bảng `borrowings` trong `borrowings_db`, sau đó gọi API để lấy dữ liệu hiển thị.

## 5. Code viết lại cho getBorrowingDetail

Code minh họa nằm trong thư mục `src/main/java/vn/ptit/librax/borrowing`.

Ý tưởng chính:

1. `borrowing-service` lấy bản ghi mượn sách từ database riêng `borrowings_db`.
2. Lấy `bookId` và `memberId` từ bản ghi đó.
3. Gọi REST API sang `book-service` để lấy `title`.
4. Gọi REST API sang `member-service` để lấy `name`.
5. Ghép dữ liệu thành `BorrowingDetailResponse`.

Đoạn xử lý chính:

```java
public BorrowingDetailResponse getBorrowingDetail(Long borrowingId) {
    Borrowing borrowing = borrowingRepository.findById(borrowingId)
            .orElseThrow(() -> new IllegalArgumentException("Borrowing not found"));

    BookResponse book = bookClient.getBookById(borrowing.getBookId());
    MemberResponse member = memberClient.getMemberById(borrowing.getMemberId());

    return new BorrowingDetailResponse(
            borrowing.getId(),
            book.id(),
            book.title(),
            member.id(),
            member.name(),
            borrowing.getStatus()
    );
}
```

Như vậy, `borrowing-service` không còn biết cấu trúc bảng `books` và `members`. Nó chỉ phụ thuộc vào contract API.

## 6. Vấn đề mới phát sinh sau khi tách database

### 6.1 Truy vấn xuyên service chậm hơn

Trước đây, một câu SQL JOIN có thể lấy đủ dữ liệu trong một lần truy vấn database. Sau khi tách service, `borrowing-service` phải gọi thêm API sang `book-service` và `member-service`. Điều này làm tăng độ trễ mạng và tăng khả năng lỗi nếu một service khác không phản hồi.

Hướng xử lý:

- Dùng cache cho các dữ liệu ít thay đổi như tên sách, tên thành viên.
- Dùng timeout và retry có giới hạn khi gọi REST API.
- Có thể lưu snapshot dữ liệu cần hiển thị tại thời điểm mượn, ví dụ `bookTitleSnapshot` và `memberNameSnapshot`.

### 6.2 Giao dịch phân tán khó hơn

Khi mỗi service có database riêng, không thể dùng một transaction database duy nhất để đảm bảo tất cả thay đổi cùng commit hoặc rollback.

Hướng xử lý:

- Dùng Saga Pattern để chia giao dịch lớn thành nhiều bước nhỏ.
- Dùng event-driven architecture cho các xử lý bất đồng bộ.
- Thiết kế idempotency để xử lý trường hợp event bị gửi lặp.

### 6.3 Dữ liệu có thể nhất quán cuối cùng

Khi dùng event hoặc cache, dữ liệu ở các service có thể không đồng bộ tức thì. Ví dụ tên sách vừa được sửa ở `book-service`, nhưng màn hình lịch sử mượn sách vẫn hiển thị tên cũ trong vài giây.

Hướng xử lý:

- Chấp nhận eventual consistency với dữ liệu không yêu cầu đồng bộ tuyệt đối.
- Phân loại rõ dữ liệu nào cần real-time, dữ liệu nào có thể dùng snapshot.
- Theo dõi event thất bại bằng dead-letter queue.

## 7. Kết luận

Đoạn code ban đầu vi phạm nguyên tắc Database-per-service vì `borrowing-service` JOIN trực tiếp sang bảng `books` và `members` thuộc service khác. Cách thiết kế lại phù hợp hơn là tách `librax_db` thành các database riêng và bắt buộc giao tiếp qua REST API hoặc event.

Thiết kế này giúp service độc lập hơn, dễ triển khai riêng, dễ bảo vệ dữ liệu và đúng tinh thần microservice. Đổi lại, hệ thống phải xử lý thêm các vấn đề mới như độ trễ khi gọi API, lỗi service phụ thuộc và giao dịch phân tán.
