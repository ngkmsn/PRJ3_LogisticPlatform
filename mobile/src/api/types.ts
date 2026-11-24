export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Role = 'ADMIN' | 'DISPATCHER' | 'DRIVER' | 'CUSTOMER'

export interface UserSummary {
  id: string
  username: string
  email: string
  role: Role
  status: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

// ---- Customer orders (order-service) ----

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED'

export interface OrderItem {
  id: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface OrderDto {
  id: string
  orderNumber: string
  status: OrderStatus
  recipientName: string
  recipientPhone: string
  deliveryAddress: string
  totalAmount: number
  notes?: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

export interface OrderStatusHistoryDto {
  id: string
  previousStatus: OrderStatus | null
  newStatus: OrderStatus
  changedByRole: string
  reason?: string | null
  createdAt: string
}

export interface CreateOrderBody {
  recipientName: string
  recipientPhone: string
  deliveryAddress: string
  notes?: string
  items: Array<{ productName: string; quantity: number; unitPrice: number }>
}

export interface OrderTrackingDto {
  orderId: string
  orderNumber: string
  orderStatus: string
  assignmentStatus: string
  driverId: string
  driverName?: string | null
  latitude: number
  longitude: number
  updatedAt: string
  liveTrackingActive: boolean
  stale: boolean
  locationAgeMinutes?: number | null
  staleWarning?: string | null
}

// ---- Driver (shipment-service / user-service) ----

export type AssignmentStatus =
  | 'PENDING'
  | 'SEARCHING_DRIVER'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'UNASSIGNED'
  | 'CANCELLED'
  | 'FAILED'

export interface DriverOrderDto {
  assignmentId: string
  orderId: string
  orderNumber: string
  recipientName: string
  recipientPhone: string
  pickupAddress?: string | null
  deliveryAddress: string
  totalAmount: number
  status: AssignmentStatus
  assignedAt?: string | null
  notes?: string | null
  createdAt: string
}

export type Availability = 'AVAILABLE' | 'UNAVAILABLE'

export interface DriverAvailabilityDto {
  driverId: string
  availability: Availability
  updatedAt: string
}

export interface DriverProfileDto {
  id: string
  fullName: string
  phoneNumber: string
  vehicleType: string
  vehiclePlate: string
  availability: Availability
  status: string
}
