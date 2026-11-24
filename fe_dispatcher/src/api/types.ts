/** Envelope returned by every backend endpoint. */
export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Role = 'ADMIN' | 'DISPATCHER' | 'DRIVER' | 'CUSTOMER'
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED'

export interface UserSummary {
  id: string
  username: string
  email: string
  role: Role
  status: UserStatus
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

/** GET /api/users items. */
export interface UserProfile {
  id: string
  userId: string
  username: string
  email: string
  role: Role
  status: UserStatus
  createdAt: string
  updatedAt: string
}

// ---- Orders (order-service) ----

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
  customerId: string
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
  orderId: string
  orderNumber: string
  previousStatus: OrderStatus | null
  newStatus: OrderStatus
  changedBy: string
  changedByRole: string
  reason?: string | null
  createdAt: string
}

// ---- Assignments (shipment-service) ----

export interface AssignmentHistoryDto {
  id: string
  orderId: string
  assignmentId: string
  previousDriverId?: string | null
  newDriverId?: string | null
  actionType: string
  reason?: string | null
  performedBy: string
  createdAt: string
}

export interface ActionableOrderItem {
  orderId: string
  orderNumber?: string
  status: string
  issueCategory: string
  issueSeverity: string
  customerId?: string
  recipientName?: string
  recipientPhone?: string
  deliveryAddress?: string
  totalAmount?: number
  createdAt: string
  updatedAt?: string
  driverId?: string
  previousDriverId?: string
  assignedAt?: string
  assignmentSource?: string
  assignedBy?: string
  unassignedReason?: string
  unassignedAt?: string
  failedAt?: string
  failureReason?: string
  failureNotes?: string
  allowedActions?: string[]
}

export interface ActionableOrdersData extends Paged<ActionableOrderItem> {
  totalActionable: number
  unassignedCount: number
  failedCount: number
}

// ---- Driver monitoring (shipment-service tracking) ----

export interface DriverMonitoringItem {
  driverId: string
  userId: string
  fullName: string
  phoneNumber: string
  vehicleType: string
  vehiclePlate: string
  status: string
  availability: 'AVAILABLE' | 'UNAVAILABLE' | string
  operationalStatus: string
  hasActiveOrder: boolean
  activeOrderId?: string | null
  activeOrderNumber?: string | null
  activeOrderStatus?: string | null
  hasGps: boolean
  latitude?: number | null
  longitude?: number | null
  gpsUpdatedAt?: string | null
  accuracy?: number | null
  speed?: number | null
  bearing?: number | null
  locationStale?: boolean | null
  locationAgeMinutes?: number | null
  staleWarning?: string | null
}

// ---- Operations dashboard ----

export interface OrderMetricsDto {
  totalOrders: number
  todayOrders: number
  pendingOrders: number
  searchingDriverOrders: number
  assignedOrders: number
  pickedUpOrders: number
  inTransitOrders: number
  deliveredOrders: number
  failedOrders: number
  cancelledOrders: number
  ordersByStatus: Record<string, number>
  recentOrders?: Array<{
    id: string
    orderNumber: string
    customerId: string
    recipientName: string
    deliveryAddress: string
    status: string
    totalAmount?: number
    createdAt: string
  }>
}

export interface DriverOperationalMetricsDto {
  totalDrivers: number
  availableDrivers: number
  busyDrivers: number
  unavailableDrivers: number
  activeGpsDrivers: number
  staleGpsDrivers: number
  driversByStatus: Record<string, number>
}

export interface AttentionItemDto {
  id: string
  type: string
  severity: string
  title: string
  description: string
  referenceId?: string
  orderNumber?: string
  age?: string
  createdAt?: string
}

export interface OperationsDashboardDto {
  orderMetrics: OrderMetricsDto
  driverMetrics: DriverOperationalMetricsDto
  attentionItems: AttentionItemDto[]
  generatedAt: string
}
