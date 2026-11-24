# Desktop Client cho Admin/Operator — Thiết kế

Ngày: 2026-11-24 · Thư mục code: `fe_dispatcher/`

## 1. Mục đích

Đồ án trên trường: nền tảng logistics cơ bản gồm mobile app (customer, driver) và desktop client (admin/operator). Tiêu chí thành công là **demo được các luồng chính, chạy thật với backend**, không cần chi tiết hay phức tạp.

Desktop client là app cho bộ phận vận hành (role `DISPATCHER`) và quản trị (`ADMIN`): theo dõi đơn, xử lý đơn chưa gán hoặc giao thất bại, theo dõi tài xế, quản lý tài khoản.

Backend đã sẵn sàng (PB-001 → PB-060). Luồng end-to-end đã được chạy thật qua gateway `http://localhost:8080`. Tài khoản mẫu: `admin/Admin@123`, `dispatcher/Dispatcher@123`, `driver/Driver@123`, `customer/Customer@123`.

## 2. Mục tiêu / Không làm / Thất bại nếu

**Mục tiêu**
- Đăng nhập thật bằng tài khoản backend; chỉ `ADMIN`, `DISPATCHER` được vào.
- Mọi màn hình trong phạm vi dùng **dữ liệu thật** từ API; xoá toàn bộ dữ liệu giả.
- Chạy được như app desktop (Electron) trên máy demo (macOS), và chạy được trên trình duyệt khi phát triển.

**Không làm**
- Realtime qua WebSocket (dùng polling), push notification, Reports, Settings nâng cao, đa ngôn ngữ, phân quyền chi tiết theo permission, ký số/cài đặt cho Windows.

**Thất bại nếu** (dù về kỹ thuật vẫn chạy)
- Còn màn hình hiển thị số liệu giả mà người xem tưởng là thật.
- Buổi demo bị kẹt vì phải sửa config/URL bằng tay, hoặc app desktop không gọi được API (CORS).

## 3. Ràng buộc

- Giữ stack hiện có: React 19, Vite 8, TypeScript 5.7, Tailwind CSS v4. Không dùng Figma Make nữa — được tái cấu trúc tự do, có thể bỏ các plugin `figma*` trong `vite.config.ts` và thư mục `.figma/`.
- Giữ ngôn ngữ giao diện tiếng Anh như bản Figma hiện tại *(quyết định của Claude — có thể đổi)*.
- Package manager: `pnpm` (theo `.mise.toml`); xoá `package-lock.json` để tránh hai lockfile.
- Mọi request đi qua API Gateway; response có dạng `{ success, message, data }`; lỗi `401` = token hỏng/hết hạn, `403` = không đủ quyền.
- Token JWT sống 24 giờ, không có refresh token.

## 4. Các hướng đã cân nhắc

Hướng tổng (đã chốt với người dùng): **React + Electron** cho desktop, **React Native (Expo)** cho mobile, dùng chung TypeScript.

Trong desktop, các lựa chọn kỹ thuật (người dùng giao cho Claude quyết):

| Vấn đề | Chọn | Bỏ qua | Lý do |
|---|---|---|---|
| Electron gọi API qua CORS | Electron phục vụ file build qua giao thức riêng `app://dispatcher`; gateway thêm origin này vào danh sách CORS mặc định | Tắt `webSecurity`; chạy server tĩnh local | `file://` gửi `Origin: null` bị gateway chặn; tắt bảo mật thì dễ bị giảng viên hỏi; server phụ thêm điểm hỏng |
| Lấy dữ liệu, polling | TanStack Query | Tự viết hook `useEffect` | Có sẵn polling, refetch sau khi gán tài xế, trạng thái loading/error |
| Điều hướng | React Router, `HashRouter` | `BrowserRouter` | Hash route chạy đúng cả trong Electron lẫn trình duyệt |
| Bản đồ tài xế | Leaflet + OpenStreetMap | Bản đồ SVG giả hiện có | Hiển thị vị trí GPS thật; cần Internet khi demo (xem rủi ro) |

## 5. Thiết kế

### 5.1 Cấu trúc thư mục

```
fe_dispatcher/
  electron/main.ts          # tạo cửa sổ, đăng ký giao thức app://
  src/
    api/client.ts           # fetch wrapper: base URL, Bearer token, bóc {success,data}, xử lý 401
    api/types.ts            # DTO dùng chung
    api/*.ts                # orders, assignments, drivers, tracking, users, dashboard
    auth/                   # AuthContext, LoginPage, RequireRole
    components/             # Icon, Badge, Button, Pagination, AppShell... tách từ App.tsx
    pages/                  # mỗi trang một file
    main.tsx, App.tsx       # router + providers
```

### 5.2 Cấu hình môi trường

- Địa chỉ server: trên màn hình đăng nhập có ô "Server" sửa được lúc chạy, lưu vào `localStorage`. Mặc định là rỗng khi chạy dev trên trình duyệt (Vite proxy `/api` → `http://localhost:8080`) và `http://localhost:8080` trong Electron (lấy từ `VITE_API_BASE_URL` lúc build). Nhờ vậy backend có thể chạy trên máy khác mà không phải build lại *(quyết định của Claude)*.
- Gateway: thêm `app://dispatcher` vào giá trị mặc định của `quarkus.http.cors.origins` (cạnh regex localhost). Origin của Electron luôn là `app://dispatcher` bất kể backend chạy ở đâu.
- Rate limit gateway: nâng giới hạn mặc định cho môi trường demo, `orders` 30 → 120 và `default` 60 → 120 request/phút/IP, vì desktop, mobile và giả lập trên cùng máy dùng chung một IP *(quyết định của Claude)*.

### 5.2b Dữ liệu demo

Seed hiện có 4 user, và tài xế `driver` đã có hồ sơ. Chưa có đơn hàng và chưa có vị trí GPS nào. Thêm script `scripts/demo-data.sh` chỉ gọi API công khai qua gateway *(quyết định của Claude)*:
- tạo thêm 2 user `DRIVER` kèm hồ sơ tài xế, bật `AVAILABLE`, gửi vị trí GPS quanh TP.HCM;
- customer tạo vài đơn: một số được tự gán, một đơn để lại chưa gán (tài xế tắt sẵn sàng), một đơn đi tới giao thất bại;
- chạy lại nhiều lần được (bỏ qua user đã tồn tại).

Script này phục vụ phát triển và làm phương án dự phòng khi demo; về sau mobile app sẽ tạo dữ liệu thật.

### 5.3 Đăng nhập

- `POST /api/auth/login` → lưu `accessToken` và `user` vào `localStorage`.
- Nếu role không phải `ADMIN`/`DISPATCHER`: không lưu, báo "Tài khoản này không dùng desktop client".
- Bất kỳ response `401` nào → xoá token, về trang đăng nhập. `403` → hiện thông báo không đủ quyền ngay trên trang.
- Nút đăng xuất ở thanh bên.

### 5.4 Màn hình và API

| Trang | Role | API | Ghi chú |
|---|---|---|---|
| Dashboard | ADMIN, DISPATCHER | `GET /api/shipments/operations/dashboard` | Đã có code gọi thật; polling 30s |
| Orders: danh sách | ADMIN, DISPATCHER | `GET /api/orders?page&size&status` | **Cần sửa backend** (5.5) |
| Order: chi tiết + lịch sử | ADMIN, DISPATCHER | `GET /api/orders/{id}`, `GET /api/orders/{id}/history`, `GET /api/shipments/assignments/{orderId}/history` | **Cần sửa backend** cho 2 API đầu (5.5) |
| Cần xử lý (gộp Assignment + Exceptions) | ADMIN, DISPATCHER | `GET /api/shipments/assignments/actionable`, `POST .../{orderId}/manual-assign`, `POST .../{orderId}/reassign` | Đã có code gọi thật; chọn tài xế từ danh sách tài xế thật |
| Drivers: danh sách + bản đồ | ADMIN, DISPATCHER | `GET /api/shipments/tracking/operations/drivers`, `GET .../operations/drivers/{driverId}` | Polling 10s; bản đồ Leaflet |
| Users (quản lý tài khoản) | ADMIN | `GET/POST /api/users`, `PATCH /api/users/{id}/status` | Tạo user, khoá/mở khoá |
| Tạo hồ sơ tài xế | ADMIN | `POST /api/drivers` (cần `userId` của user role `DRIVER`) | Luồng: tạo user DRIVER → tạo hồ sơ |

Reports và Settings bị bỏ khỏi menu. Trang Assignment và Exceptions hiện có được gộp thành một trang "Cần xử lý" vì cùng dùng một API.

### 5.5 Bổ sung backend (order-service)

Hiện `GET /orders` và `GET /orders/{id}` chỉ cho `CUSTOMER` xem đơn của mình. Sửa để:
- `ADMIN`, `DISPATCHER`: xem toàn bộ đơn (lọc theo `status`, phân trang, mới nhất trước) và chi tiết, lịch sử bất kỳ đơn nào.
- `CUSTOMER`: giữ nguyên hành vi cũ (chỉ đơn của mình, IDOR vẫn bị chặn).
- `DRIVER`: vẫn bị từ chối (403).
Kèm test cho cả 4 role.

### 5.6 Electron

- Dev: `pnpm dev` (trình duyệt) và `pnpm electron:dev` (Electron mở `http://localhost:5173`).
- Bản demo: `pnpm electron:build` → build Vite rồi đóng gói bằng `electron-builder --dir` thành `.app` chạy được trên macOS (không ký số).
- Cửa sổ 1440×900, tối thiểu 1200×760; `contextIsolation: true`, `nodeIntegration: false`.

## 6. Kiểm thử

- `pnpm typecheck` và `pnpm build` phải sạch.
- Vitest cho `api/client.ts` (bóc response, xử lý 401/403) và logic chặn role.
- Kiểm tra tay theo kịch bản demo, với backend thật đang chạy: đăng nhập dispatcher → dashboard → mở đơn → xử lý đơn chưa gán bằng gán tay → xem tài xế trên bản đồ; đăng nhập admin → tạo user DRIVER → tạo hồ sơ tài xế → tài xế đó xuất hiện khi gán đơn. Đăng nhập bằng `customer` phải bị từ chối.

## 7. Rủi ro

- Bản đồ OpenStreetMap cần Internet. Nếu mất mạng, vẫn hiển thị toạ độ dạng chữ.
- Rate limit gateway tính theo IP: đã nâng giới hạn (5.2). Ngoài ra, chỉ polling ở trang đang mở; trang Orders không polling, chỉ có nút làm mới.

## 8. Để người làm tự quyết

Bố cục chi tiết từng trang (bám theo thiết kế Figma hiện có), cách chia component, thời gian polling cụ thể, thông báo lỗi, icon ứng dụng.
