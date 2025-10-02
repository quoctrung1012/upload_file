# 📘 Java `String.format()` Format Specifier Cheat Sheet

Dưới đây là bảng tổng hợp các định dạng thường dùng và nâng cao khi sử dụng `String.format()` trong Java:

---

## 🔤 1. Định dạng Chuỗi (`String`)

| Mẫu         | Mô tả                            | Ví dụ                            |
|-------------|----------------------------------|-----------------------------------|
| `%s`        | Chuỗi                            | `"Hello"` → `Hello`              |
| `%20s`      | Căn phải, rộng 20 ký tự           | `"Hi"` → `                  Hi`   |
| `%-20s`     | Căn trái, rộng 20 ký tự           | `"Hi"` → `Hi                  `   |

---

## 🔢 2. Định dạng Số Nguyên (`int`, `long`)

| Mẫu         | Mô tả                            | Ví dụ                            |
|-------------|----------------------------------|-----------------------------------|
| `%d`        | Số nguyên thập phân              | `42` → `42`                       |
| `%05d`      | Thêm số 0 ở đầu (5 ký tự)        | `42` → `00042`                    |
| `%,d`       | Thêm dấu phẩy phân cách hàng nghìn| `1000000` → `1,000,000`          |
| `%-6d`      | Căn trái, rộng 6 ký tự           | `42` → `42    `                   |

---

## 🧮 3. Định dạng Số Thực (`float`, `double`)

| Mẫu         | Mô tả                            | Ví dụ                            |
|-------------|----------------------------------|-----------------------------------|
| `%f`        | Số thực (mặc định 6 chữ số sau dấu phẩy) | `3.14159` → `3.141590`      |
| `%.2f`      | Làm tròn 2 chữ số sau dấu phẩy    | `3.14159` → `3.14`               |
| `%8.2f`     | Rộng 8 ký tự, 2 số lẻ             | `3.14` → `    3.14`              |
| `%,.2f`     | Thêm dấu phẩy + 2 số lẻ           | `1234567.89` → `1,234,567.89`    |

---

## 🕒 4. Định dạng Ngày (`java.util.Date`, `LocalDateTime`, `Instant`...)

| Mẫu         | Mô tả                            | Ví dụ (`Date`)                  |
|-------------|----------------------------------|----------------------------------|
| `%tY`       | Năm đầy đủ (4 chữ số)            | `2025`                           |
| `%ty`       | Năm 2 chữ số                     | `25`                             |
| `%tm`       | Tháng (01–12)                    | `06`                             |
| `%td`       | Ngày trong tháng (01–31)         | `26`                             |
| `%tH:%tM`   | Giờ:Phút (24h)                    | `14:35`                          |
| `%tF`       | ISO date (YYYY-MM-DD)            | `2025-06-26`                     |
| `%tT`       | ISO time (HH:MM:SS)              | `14:35:00`                       |
| `%1$tY-%1$tm-%1$td` | Định dạng custom        | `2025-06-26`                     |

> 📌 Lưu ý: cần truyền `Date` vào nhiều lần nếu dùng nhiều `%tX`

---

## 🔁 5. Định dạng Lặp (Indexed)

| Mẫu             | Mô tả                          | Ví dụ                          |
|------------------|--------------------------------|---------------------------------|
| `%1$s - %2$d`     | Truy cập đối số theo thứ tự    | `"File", 3` → `File - 3`        |
| `%2$s - %1$d`     | Đổi thứ tự                     | `10, "Hello"` → `Hello - 10`    |

---

## 🧪 Ví dụ Tổng hợp

```java
String name = "invoice";
int index = 7;
double size = 14.23;
Date now = new Date();

String formatted = String.format(
    "File: %-10s | Index: %02d | Size: %.2f MB | Date: %tF %tT",
    name, index, size, now, now
);
// Output: File: invoice    | Index: 07 | Size: 14.23 MB | Date: 2025-06-26 14:35:00
