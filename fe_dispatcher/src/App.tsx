import { useState, useEffect, useCallback, type ReactNode } from "react"

type Page = "Dashboard" | "Orders" | "Assignment" | "Drivers" | "Exceptions" | "Reports" | "Settings"
type IconName = "grid" | "box" | "route" | "truck" | "alert" | "chart" | "settings" | "search" | "bell" | "chevron" | "dots" | "filter" | "download" | "refresh" | "check" | "clock" | "user" | "close" | "arrow" | "bolt"

const paths: Record<IconName, ReactNode> = {
  grid: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </>
  ),
  box: (
    <>
      <path d="m21 8-9-5-9 5 9 5 9-5Z" />
      <path d="m3 8 9 5 9-5M12 13v9" />
      <path d="m21 8v9l-9 5-9-5V8" />
    </>
  ),
  route: (
    <>
      <circle cx="6" cy="18" r="3" />
      <circle cx="18" cy="6" r="3" />
      <path d="M8.5 16.5c3-1 1-5 4-6s3.5-2 3.5-2" />
    </>
  ),
  truck: (
    <>
      <path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z" />
      <circle cx="7" cy="18" r="2" />
      <circle cx="18" cy="18" r="2" />
    </>
  ),
  alert: (
    <>
      <path d="M12 3 2.8 20h18.4L12 3Z" />
      <path d="M12 9v5M12 17.5v.1" />
    </>
  ),
  chart: (
    <>
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </>
  ),
  bell: (
    <>
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
    </>
  ),
  chevron: <path d="m9 18 6-6-6-6" />,
  dots: (
    <>
      <circle cx="5" cy="12" r="1" />
      <circle cx="12" cy="12" r="1" />
      <circle cx="19" cy="12" r="1" />
    </>
  ),
  filter: <path d="M4 5h16M7 12h10M10 19h4" />,
  download: (
    <>
      <path d="M12 3v12m0 0 4-4m-4 4-4-4M4 19h16" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 7v5h-5M4 17v-5h5" />
      <path d="M6.1 8A7 7 0 0 1 18.5 6M17.9 16A7 7 0 0 1 5.5 18" />
    </>
  ),
  check: <path d="m5 12 4 4L19 6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v6l4 2" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </>
  ),
  close: <path d="m6 6 12 12M18 6 6 18" />,
  arrow: <path d="m5 12 14 0m-5-5 5 5-5 5" />,
  bolt: <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />,
}

function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  )
}

const orders = [
  {
    id: "SXP-240519-8842",
    time: "10:42",
    customer: "Maya Chen",
    pickup: "Westgate Mall, Jurong",
    delivery: "18 Boon Lay Way",
    status: "ASSIGNING",
    driver: "—",
    assignment: "Retry 3 of 3",
    eta: "11:55",
    priority: "Urgent",
    age: "14m 22s",
  },
  {
    id: "SXP-240519-8839",
    time: "10:39",
    customer: "Andre Tan",
    pickup: "51 Ubi Ave 1",
    delivery: "Marina Bay Financial Ctr",
    status: "IN_TRANSIT",
    driver: "D. Rahman",
    assignment: "Assigned",
    eta: "11:28",
    priority: "Normal",
    age: "37m 04s",
  },
  {
    id: "SXP-240519-8831",
    time: "10:31",
    customer: "Nur Aisyah",
    pickup: "Tampines Central",
    delivery: "Changi Business Park",
    status: "PICKED_UP",
    driver: "L. Wei",
    assignment: "Assigned",
    eta: "11:19",
    priority: "Normal",
    age: "29m 41s",
  },
  {
    id: "SXP-240519-8827",
    time: "10:27",
    customer: "Owen Lim",
    pickup: "Novena Square",
    delivery: "Bukit Timah Plaza",
    status: "PROCESSING",
    driver: "—",
    assignment: "Pending",
    eta: "11:42",
    priority: "High",
    age: "8m 13s",
  },
  {
    id: "SXP-240519-8818",
    time: "10:18",
    customer: "Siti Hajar",
    pickup: "Paya Lebar Quarter",
    delivery: "Kallang Wave Mall",
    status: "FAILED",
    driver: "J. Kumar",
    assignment: "Reassign req.",
    eta: "—",
    priority: "Urgent",
    age: "48m 09s",
  },
  {
    id: "SXP-240519-8812",
    time: "10:12",
    customer: "Marcus Goh",
    pickup: "Alexandra Retail Ctr",
    delivery: "Sentosa Gateway",
    status: "ASSIGNED",
    driver: "F. Ismail",
    assignment: "Assigned",
    eta: "11:14",
    priority: "Normal",
    age: "21m 52s",
  },
  {
    id: "SXP-240519-8804",
    time: "10:04",
    customer: "Rina Patel",
    pickup: "Orchard Central",
    delivery: "Holland Village",
    status: "DELIVERED",
    driver: "T. Nguyen",
    assignment: "Assigned",
    eta: "10:51",
    priority: "Normal",
    age: "47m 18s",
  },
]

const exceptions = [
  {
    id: "EXC-4198",
    order: "SXP-240519-8842",
    type: "Assignment timeout",
    severity: "Critical",
    created: "10:51",
    age: "8m",
    state: "Open",
    operator: "Unassigned",
  },
  {
    id: "EXC-4197",
    order: "SXP-240519-8818",
    type: "Driver became unavailable",
    severity: "High",
    created: "10:45",
    age: "14m",
    state: "Investigating",
    operator: "You",
  },
  {
    id: "EXC-4194",
    order: "SXP-240519-8799",
    type: "Delivery delayed",
    severity: "High",
    created: "10:31",
    age: "28m",
    state: "Open",
    operator: "J. Lee",
  },
  {
    id: "EXC-4191",
    order: "SXP-240519-8774",
    type: "No available driver",
    severity: "Medium",
    created: "10:17",
    age: "42m",
    state: "Open",
    operator: "Unassigned",
  },
  {
    id: "EXC-4188",
    order: "SXP-240519-8732",
    type: "Driver rejected order",
    severity: "Medium",
    created: "09:58",
    age: "1h 01m",
    state: "Monitoring",
    operator: "K. Wong",
  },
  {
    id: "EXC-4181",
    order: "SXP-240519-8691",
    type: "Order stuck in PICKED_UP",
    severity: "Low",
    created: "09:29",
    age: "1h 30m",
    state: "Investigating",
    operator: "J. Lee",
  },
]

export interface DriverMonitoringItem {
  id: string
  name: string
  phone: string
  status: "AVAILABLE" | "BUSY" | "OFFLINE" | "SUSPENDED"
  order: string
  orderStatus?: string
  workload: string
  vehicle: string
  plate: string
  activity: string
  completed: number
  failed: number
  hasGps: boolean
  latitude?: number
  longitude?: number
  gpsUpdatedAt?: string
  locationStale?: boolean
  accuracy?: number
  speed?: number
}

const drivers: DriverMonitoringItem[] = [
  {
    id: "DRV-1048",
    name: "Danish Rahman",
    phone: "0901 234 567",
    status: "BUSY",
    order: "SXP-240519-8839",
    orderStatus: "IN_TRANSIT",
    workload: "2 active",
    vehicle: "Motorcycle",
    plate: "29A-883.91",
    activity: "1m ago",
    completed: 12,
    failed: 0,
    hasGps: true,
    latitude: 21.028511,
    longitude: 105.854444,
    gpsUpdatedAt: "2m ago",
    locationStale: false,
    accuracy: 4.8,
    speed: 28.5,
  },
  {
    id: "DRV-0923",
    name: "Li Wei",
    phone: "0902 345 678",
    status: "BUSY",
    order: "SXP-240519-8831",
    orderStatus: "PICKED_UP",
    workload: "1 active",
    vehicle: "Van",
    plate: "29B-992.33",
    activity: "2m ago",
    completed: 8,
    failed: 0,
    hasGps: true,
    latitude: 21.0335,
    longitude: 105.845,
    gpsUpdatedAt: "1m ago",
    locationStale: false,
    accuracy: 3.2,
    speed: 15.0,
  },
  {
    id: "DRV-1155",
    name: "Farid Ismail",
    phone: "0903 456 789",
    status: "BUSY",
    order: "SXP-240519-8812",
    orderStatus: "ASSIGNED",
    workload: "2 active",
    vehicle: "Motorcycle",
    plate: "29A-115.54",
    activity: "3m ago",
    completed: 10,
    failed: 1,
    hasGps: true,
    latitude: 21.015,
    longitude: 105.86,
    gpsUpdatedAt: "4m ago",
    locationStale: false,
    accuracy: 5.5,
    speed: 0.0,
  },
  {
    id: "DRV-0881",
    name: "Thu Nguyen",
    phone: "0904 567 890",
    status: "AVAILABLE",
    order: "—",
    workload: "0 active",
    vehicle: "Car",
    plate: "30E-881.02",
    activity: "Just now",
    completed: 9,
    failed: 0,
    hasGps: true,
    latitude: 21.04,
    longitude: 105.83,
    gpsUpdatedAt: "35m ago",
    locationStale: true,
    accuracy: 12.0,
    speed: 0.0,
  },
  {
    id: "DRV-1210",
    name: "Alvin Teo",
    phone: "0905 678 901",
    status: "AVAILABLE",
    order: "—",
    workload: "1 queued",
    vehicle: "Van",
    plate: "29D-121.05",
    activity: "1m ago",
    completed: 7,
    failed: 0,
    hasGps: true,
    latitude: 21.022,
    longitude: 105.82,
    gpsUpdatedAt: "Just now",
    locationStale: false,
    accuracy: 4.0,
    speed: 14.2,
  },
  {
    id: "DRV-0732",
    name: "Jay Kumar",
    phone: "0906 789 012",
    status: "OFFLINE",
    order: "—",
    workload: "0 active",
    vehicle: "Motorcycle",
    plate: "29A-732.18",
    activity: "14m ago",
    completed: 6,
    failed: 2,
    hasGps: false,
    gpsUpdatedAt: "No GPS recorded",
    locationStale: false,
  },
  {
    id: "DRV-1009",
    name: "Sofia Malik",
    phone: "0907 890 123",
    status: "SUSPENDED",
    order: "—",
    workload: "0 active",
    vehicle: "Car",
    plate: "30F-100.99",
    activity: "2d ago",
    completed: 0,
    failed: 0,
    hasGps: false,
    gpsUpdatedAt: "No GPS recorded",
    locationStale: false,
  },
]

function Badge({ children, tone }: { children: ReactNode; tone?: string }) {
  const t =
    tone ||
    String(children).toLowerCase().replaceAll("_", "-").replaceAll(" ", "-")
  return <span className={`badge badge-${t}`}>{children}</span>
}

function Button({
  children,
  icon,
  variant = "secondary",
  onClick,
}: {
  children: ReactNode
  icon?: IconName
  variant?: string
  onClick?: () => void
}) {
  return (
    <button className={`btn btn-${variant}`} onClick={onClick}>
      {icon && <Icon name={icon} size={16} />} {children}
    </button>
  )
}

function SectionTitle({
  title,
  meta,
  action,
}: {
  title: string
  meta?: string
  action?: ReactNode
}) {
  return (
    <div className="section-title">
      <div>
        <h2>{title}</h2>
        {meta && <p>{meta}</p>}
      </div>
      {action}
    </div>
  )
}

function FilterBar({
  search = "Search by order ID, customer or phone",
}: {
  search?: string
}) {
  return (
    <div className="filterbar">
      <label className="table-search">
        <Icon name="search" size={16} />
        <input placeholder={search} />
      </label>
      <button className="filter-control">
        Status <span>All</span>
        <span>⌄</span>
      </button>
      <button className="filter-control hide-small">
        Priority <span>All</span>
        <span>⌄</span>
      </button>
      <button className="filter-control hide-small">
        Created date <span>Today</span>
        <span>⌄</span>
      </button>
      <Button icon="filter">More filters</Button>
    </div>
  )
}

function Pagination({ label = "Showing 1–7 of 2,431" }: { label?: string }) {
  return (
    <div className="pagination">
      <span>{label}</span>
      <div>
        <button disabled>‹</button>
        <button className="active">1</button>
        <button>2</button>
        <button>3</button>
        <span>…</span>
        <button>348</button>
        <button>›</button>
      </div>
    </div>
  )
}

function AppShell({
  page,
  setPage,
  children,
}: {
  page: Page
  setPage: (p: Page) => void
  children: ReactNode
}) {
  const nav: [Page, IconName, string?][] = [
    ["Dashboard", "grid"],
    ["Orders", "box"],
    ["Assignment", "route", "12"],
    ["Drivers", "truck"],
    ["Exceptions", "alert", "7"],
    ["Reports", "chart"],
    ["Settings", "settings"],
  ]
  return (
    <div className="app-shell">
      <aside>
        <div className="brand">
          <div className="brand-mark">
            <Icon name="route" size={20} />
          </div>
          <div>
            <strong>RelayOps</strong>
            <span>Logistics control</span>
          </div>
        </div>
        <nav>
          {nav.map(([label, icon, count]) => (
            <button
              key={label}
              className={page === label ? "active" : ""}
              onClick={() => setPage(label)}
            >
              <Icon name={icon} />
              <span>{label}</span>
              {count && <em>{count}</em>}
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="avatar">AK</div>
          <div>
            <strong>Alex Kim</strong>
            <span>Senior Dispatcher</span>
          </div>
          <Icon name="dots" size={18} />
        </div>
      </aside>
      <div className="workspace">
        <header>
          <div className="page-heading">
            <span>Operations</span>
            <b>/</b>
            <strong>{page}</strong>
          </div>
          <div className="header-actions">
            <label className="global-search">
              <Icon name="search" size={16} />
              <input placeholder="Search orders, drivers…" />
              <kbd>⌘ K</kbd>
            </label>
            <div className="system-health">
              <i />
              All systems operational
            </div>
            <button className="icon-button">
              <Icon name="bell" />
              <span className="notification-dot" />
            </button>
            <div className="header-user">
              <div className="avatar">AK</div>
              <span>Alex Kim</span>
              <span>⌄</span>
            </div>
          </div>
        </header>
        <main>{children}</main>
      </div>
    </div>
  )
}

interface OrderMetricsDto {
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
}

interface DriverOperationalMetricsDto {
  totalDrivers: number
  availableDrivers: number
  busyDrivers: number
  unavailableDrivers: number
  activeGpsDrivers: number
  staleGpsDrivers: number
  driversByStatus: Record<string, number>
}

interface AttentionItemDto {
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

interface OperationsDashboardDto {
  orderMetrics: OrderMetricsDto
  driverMetrics: DriverOperationalMetricsDto
  attentionItems: AttentionItemDto[]
  generatedAt: string
}

function Dashboard({
  openOrder,
  goExceptions,
}: {
  openOrder: (id: string) => void
  goExceptions: () => void
}) {
  const [dashboardData, setDashboardData] = useState<OperationsDashboardDto | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [refreshing, setRefreshing] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const [isAccessDenied, setIsAccessDenied] = useState<boolean>(false)
  const [lastUpdatedSec, setLastUpdatedSec] = useState<number>(0)

  const loadDashboard = useCallback(async (forceRefresh = false) => {
    if (forceRefresh) setRefreshing(true)
    else setLoading(true)
    setError(null)
    setIsAccessDenied(false)

    try {
      const token = localStorage.getItem("token") || "Bearer sample.dispatcher.jwt"
      const url = forceRefresh
        ? "/api/shipments/operations/dashboard?refresh=true"
        : "/api/shipments/operations/dashboard"

      const res = await fetch(url, {
        headers: {
          Accept: "application/json",
          Authorization: token.startsWith("Bearer ") ? token : `Bearer ${token}`,
        },
      })

      if (res.status === 401 || res.status === 403) {
        setIsAccessDenied(true)
        setError("Access denied: You must have Operations or Dispatcher privileges to view this dashboard.")
        setLoading(false)
        setRefreshing(false)
        return
      }

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`)
      }

      const json = await res.json()
      if (json && json.data) {
        setDashboardData(json.data)
        setLastUpdatedSec(0)
      }
    } catch (err: any) {
      console.warn("Unable to fetch live operations dashboard from backend:", err.message)
      // Display graceful fallback state with informative indicator
      setError("Unable to sync with live backend services. Displaying cached operational snapshot.")
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    loadDashboard(false)
    const interval = setInterval(() => {
      setLastUpdatedSec((sec) => sec + 5)
    }, 5000)
    return () => clearInterval(interval)
  }, [loadDashboard])

  if (isAccessDenied) {
    return (
      <div className="page">
        <div className="panel" style={{ textAlign: "center", padding: "64px 24px", color: "#ef4444" }}>
          <div style={{ display: "flex", justifyContent: "center", marginBottom: 16 }}>
            <Icon name="alert" size={48} />
          </div>
          <h2 style={{ fontSize: "1.25rem", fontWeight: 700, marginBottom: 8 }}>Access Denied (403 Forbidden)</h2>
          <p style={{ color: "#6b7280", maxWidth: 480, margin: "0 auto 24px auto" }}>
            Only Operations and Dispatcher accounts are permitted to view the Operations Dashboard. Please log in with authorized credentials.
          </p>
          <Button onClick={() => loadDashboard(true)}>Retry with authorized token</Button>
        </div>
      </div>
    )
  }

  // Derive metrics from live backend data if available, with resilient defaults
  const totalOrders = dashboardData ? dashboardData.orderMetrics.totalOrders : 2431
  const pendingOrders = dashboardData ? dashboardData.orderMetrics.pendingOrders : 84
  const assignedOrders = dashboardData ? (dashboardData.orderMetrics.searchingDriverOrders || 0) + (dashboardData.orderMetrics.assignedOrders || 0) : 31
  const inDeliveryOrders = dashboardData ? dashboardData.orderMetrics.inTransitOrders : 612
  const deliveredOrders = dashboardData ? dashboardData.orderMetrics.deliveredOrders : 1647
  const failedOrders = dashboardData ? dashboardData.orderMetrics.failedOrders : 57
  const availDrivers = dashboardData ? dashboardData.driverMetrics.availableDrivers : 48
  const busyDrivers = dashboardData ? dashboardData.driverMetrics.busyDrivers : 22
  const activeGps = dashboardData ? dashboardData.driverMetrics.activeGpsDrivers : 65

  const kpis = [
    ["Total orders today", totalOrders.toLocaleString(), "+8.2%", "vs yesterday", "neutral"],
    ["Waiting processing", pendingOrders.toLocaleString(), pendingOrders > 0 ? `${pendingOrders} queue` : "normal", "orders pending", pendingOrders > 50 ? "warning" : "neutral"],
    ["Being assigned", assignedOrders.toLocaleString(), `${availDrivers} avail`, "drivers ready", "blue"],
    ["In delivery", inDeliveryOrders.toLocaleString(), `${busyDrivers} busy`, "active drivers", "purple"],
    ["Delivered", deliveredOrders.toLocaleString(), "+6.4%", "completed", "success"],
    ["Failed / exception", failedOrders.toLocaleString(), failedOrders > 0 ? "action needed" : "0.0%", "requires review", failedOrders > 0 ? "danger" : "neutral"],
    ["Fleet status", `${availDrivers + busyDrivers} total`, `${activeGps} active GPS`, "live telemetry", "neutral"],
  ]

  const attentionList = dashboardData?.attentionItems && dashboardData.attentionItems.length > 0
    ? dashboardData.attentionItems
    : null

  const isEmpty = dashboardData && dashboardData.orderMetrics.totalOrders === 0 && dashboardData.driverMetrics.totalDrivers === 0

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Operations dashboard</h1>
          <p>
            Live overview of today's logistics operation · {new Date().toLocaleDateString("en-US", { weekday: "long", year: "numeric", month: "long", day: "numeric" })}
          </p>
        </div>
        <div className="updated" style={{ display: "flex", alignItems: "center", gap: 12 }}>
          {loading && <span style={{ color: "#3b82f6", fontSize: "0.85rem" }}>Loading dashboard...</span>}
          <span>
            <Icon name="refresh" size={14} /> Updated {lastUpdatedSec}s ago
          </span>
          <Button onClick={() => loadDashboard(true)}>
            {refreshing ? "Refreshing..." : "Refresh"}
          </Button>
        </div>
      </div>

      {error && !isAccessDenied && (
        <div style={{ backgroundColor: "#fef3c7", color: "#92400e", padding: "10px 16px", borderRadius: 8, marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{error}</span>
          <button className="text-button" onClick={() => loadDashboard(true)} style={{ color: "#92400e", fontWeight: 600 }}>
            Retry
          </button>
        </div>
      )}

      {isEmpty ? (
        <div className="panel" style={{ textAlign: "center", padding: "48px 24px", color: "#6b7280" }}>
          <Icon name="box" size={40} />
          <h3 style={{ marginTop: 12, fontWeight: 600 }}>No operations activity recorded yet today</h3>
          <p style={{ marginTop: 4 }}>Orders and drivers will automatically appear as shipments are booked and assigned.</p>
        </div>
      ) : (
        <>
          <div className="kpi-grid">
            {kpis.map(([label, value, delta, sub, tone]) => (
              <div className={`kpi kpi-${tone}`} key={label}>
                <div className="kpi-label">
                  {label}
                  <Icon name={tone === "danger" ? "alert" : "dots"} size={16} />
                </div>
                <strong>{value}</strong>
                <div>
                  <b>{delta}</b> {sub}
                </div>
              </div>
            ))}
          </div>

          <div className="dashboard-grid">
            <section className="panel attention">
              <SectionTitle
                title="Requires attention"
                meta={attentionList ? `${attentionList.length} operational issues require intervention` : "Operational exceptions that may need dispatcher intervention"}
                action={
                  <button className="text-button" onClick={goExceptions}>
                    View all exceptions <Icon name="arrow" size={14} />
                  </button>
                }
              />
              <div className="attention-list">
                {attentionList && attentionList.length > 0 ? (
                  attentionList.map((x) => (
                    <button
                      className="attention-row"
                      key={x.id}
                      onClick={() => openOrder(x.orderNumber || x.referenceId || "ORD-RECENT")}
                    >
                      <div
                        className={`severity-icon severity-${(x.severity || "high").toLowerCase()}`}
                      >
                        <Icon name="alert" size={17} />
                      </div>
                      <div className="attention-main">
                        <div>
                          <strong>{x.type.replaceAll("_", " ")}</strong>
                          <Badge tone={(x.severity || "medium").toLowerCase()}>{x.severity}</Badge>
                        </div>
                        <span>
                          {x.title} · {x.description}
                        </span>
                      </div>
                      <div className="attention-age">
                        <span>{x.age || "Active"}</span>
                        <Icon name="chevron" size={16} />
                      </div>
                    </button>
                  ))
                ) : (
                  exceptions.slice(0, 4).map((x, i) => (
                    <button
                      className="attention-row"
                      key={x.id}
                      onClick={() => openOrder(x.order)}
                    >
                      <div
                        className={`severity-icon severity-${x.severity.toLowerCase()}`}
                      >
                        <Icon name="alert" size={17} />
                      </div>
                      <div className="attention-main">
                        <div>
                          <strong>{x.type}</strong>
                          <Badge tone={x.severity.toLowerCase()}>{x.severity}</Badge>
                        </div>
                        <span>
                          {x.order} ·{" "}
                          {i === 0
                            ? "3 automatic attempts failed"
                            : i === 1
                              ? "Assigned driver went offline"
                              : i === 2
                                ? "ETA exceeded by 28 minutes"
                                : "No candidates within service radius"}
                        </span>
                      </div>
                      <div className="attention-age">
                        <span>{x.age}</span>
                        <Icon name="chevron" size={16} />
                      </div>
                    </button>
                  ))
                )}
              </div>
            </section>

            <section className="panel status-panel">
              <SectionTitle title="Order status" meta={`${totalOrders.toLocaleString()} total orders`} />
              <div className="donut-wrap">
                <div className="donut">
                  <div>
                    <strong>{totalOrders.toLocaleString()}</strong>
                    <span>Total orders</span>
                  </div>
                </div>
                <div className="legend">
                  {[
                    ["Delivered", deliveredOrders.toLocaleString(), totalOrders > 0 ? `${((deliveredOrders / totalOrders) * 100).toFixed(1)}%` : "0%"],
                    ["In delivery", inDeliveryOrders.toLocaleString(), totalOrders > 0 ? `${((inDeliveryOrders / totalOrders) * 100).toFixed(1)}%` : "0%"],
                    ["Processing", pendingOrders.toLocaleString(), totalOrders > 0 ? `${((pendingOrders / totalOrders) * 100).toFixed(1)}%` : "0%"],
                    ["Failed", failedOrders.toLocaleString(), totalOrders > 0 ? `${((failedOrders / totalOrders) * 100).toFixed(1)}%` : "0%"],
                  ].map((x, i) => (
                    <div key={x[0]}>
                      <i className={`dot-${i}`} />
                      <span>{x[0]}</span>
                      <strong>{x[1]}</strong>
                      <em>{x[2]}</em>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          </div>
        </>
      )}
      <div className="dashboard-grid lower">
        <section className="panel chart-panel">
          <SectionTitle
            title="Orders created over time"
            meta="Today, hourly"
            action={<button className="select-button">Orders created⌄</button>}
          />
          <div className="chart">
            <div className="y-labels">
              <span>300</span>
              <span>200</span>
              <span>100</span>
              <span>0</span>
            </div>
            <div className="bars">
              {[36, 49, 42, 57, 66, 78, 71, 88, 64, 51].map((h, i) => (
                <div key={i}>
                  <span style={{ height: `${h}%` }} />
                  <em>{`${i + 7}:00`}</em>
                </div>
              ))}
            </div>
          </div>
        </section>
        <section className="panel health-panel">
          <SectionTitle
            title="Operational health"
            meta="Compared with target SLA"
          />
          <div className="health-list">
            {[
              [
                "Assignment success rate",
                "94.8%",
                "Target ≥ 95%",
                "warning",
                95,
              ],
              [
                "Average processing time",
                "2m 18s",
                "Target ≤ 3 min",
                "success",
                76,
              ],
              [
                "Average delivery time",
                "41m 06s",
                "Target ≤ 45 min",
                "success",
                82,
              ],
              ["On-time completion", "91.2%", "Target ≥ 90%", "success", 91],
            ].map(([a, b, c, t, w]) => (
              <div className="health-row" key={a}>
                <div>
                  <span>{a}</span>
                  <strong>{b}</strong>
                </div>
                <div className="progress">
                  <i className={`progress-${t}`} style={{ width: `${w}%` }} />
                </div>
                <small>{c}</small>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}

function OrdersPage({ openOrder }: { openOrder: (id: string) => void }) {
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Orders</h1>
          <p>Monitor and manage all delivery orders</p>
        </div>
        <Button icon="download">Export</Button>
      </div>
      <div className="subtabs">
        <button className="active">
          All orders <span>2,431</span>
        </button>
        <button>
          Active <span>842</span>
        </button>
        <button>
          Needs attention <span>12</span>
        </button>
        <button>
          Completed <span>1,647</span>
        </button>
      </div>
      <section className="panel table-panel">
        <FilterBar />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <input type="checkbox" />
                </th>
                <th>Order ID ↕</th>
                <th>Created</th>
                <th>Customer</th>
                <th>Pickup → Delivery</th>
                <th>Status</th>
                <th>Driver</th>
                <th>Assignment</th>
                <th>Age</th>
                <th>ETA</th>
                <th>Priority</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id} onClick={() => openOrder(o.id)}>
                  <td onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" />
                  </td>
                  <td>
                    <strong className="link">{o.id}</strong>
                  </td>
                  <td>
                    {o.time}
                    <small>Today</small>
                  </td>
                  <td>{o.customer}</td>
                  <td className="route-cell">
                    <span>{o.pickup}</span>
                    <small>
                      <Icon name="arrow" size={11} /> {o.delivery}
                    </small>
                  </td>
                  <td>
                    <Badge>{o.status}</Badge>
                  </td>
                  <td>{o.driver}</td>
                  <td>{o.assignment}</td>
                  <td className={o.id.endsWith("8842") ? "danger-text" : ""}>
                    {o.age}
                  </td>
                  <td>{o.eta}</td>
                  <td>
                    <Badge>{o.priority}</Badge>
                  </td>
                  <td>
                    <Icon name="dots" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination />
      </section>
    </div>
  )
}

function AssignmentPage({
  openModal,
  openOrder,
}: {
  openModal: () => void
  openOrder: (id: string) => void
}) {
  const stats = [
    ["Waiting", "24", "clock", "warning"],
    ["In progress", "31", "refresh", "blue"],
    ["Assigned today", "2,302", "check", "success"],
    ["Failed / timeout", "12", "alert", "danger"],
    ["Reassignment", "5", "route", "purple"],
  ]
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Assignment management</h1>
          <p>
            Monitor automatic assignment performance and intervene when required
          </p>
        </div>
        <div className="auto-label">
          <i />
          <Icon name="bolt" size={15} /> Automatic assignment active
        </div>
      </div>
      <div className="summary-strip">
        {stats.map(([a, b, c, d]) => (
          <div key={a}>
            <span className={`summary-icon tone-${d}`}>
              <Icon name={c as IconName} />
            </span>
            <p>
              {a}
              <strong>{b}</strong>
            </p>
          </div>
        ))}
      </div>
      <section className="panel table-panel">
        <SectionTitle
          title="Assignment queue"
          meta="Orders waiting, in progress, or requiring intervention"
        />
        <FilterBar search="Search order ID" />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Created</th>
                <th>Attempt</th>
                <th>Current status</th>
                <th>Candidates</th>
                <th>Method</th>
                <th>Failure reason</th>
                <th>Time waiting</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {orders.slice(0, 6).map((o, i) => (
                <tr key={o.id} className={i === 0 ? "overdue" : ""}>
                  <td>
                    <strong className="link" onClick={() => openOrder(o.id)}>
                      {o.id}
                    </strong>
                  </td>
                  <td>{o.time}</td>
                  <td>{i === 0 ? "3 of 3" : i === 4 ? "1 of 3" : "1 of 3"}</td>
                  <td>
                    <Badge>
                      {i === 0
                        ? "TIMEOUT"
                        : i === 4
                          ? "FAILED"
                          : i === 3
                            ? "WAITING"
                            : "IN_PROGRESS"}
                    </Badge>
                  </td>
                  <td>{[0, 4, 6, 2, 1, 3][i]} drivers</td>
                  <td>{i === 4 ? "Manual" : "Automatic"}</td>
                  <td>
                    {i === 0
                      ? "No driver accepted"
                      : i === 4
                        ? "Driver unavailable"
                        : "—"}
                  </td>
                  <td className={i === 0 ? "danger-text" : ""}>
                    {
                      [
                        "14m 22s",
                        "3m 11s",
                        "2m 47s",
                        "1m 08s",
                        "9m 32s",
                        "42s",
                      ][i]
                    }
                  </td>
                  <td>
                    {i === 0 || i === 4 ? (
                      <Button variant="primary" onClick={openModal}>
                        Assign manually
                      </Button>
                    ) : (
                      <button className="icon-button">
                        <Icon name="dots" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination label="Showing 1–6 of 67" />
      </section>
    </div>
  )
}

function DriverDetailModal({
  driver,
  close,
  openOrder,
}: {
  driver: DriverMonitoringItem
  close: () => void
  openOrder?: (id: string) => void
}) {
  return (
    <div className="modal-backdrop">
      <div className="modal" style={{ maxWidth: 680 }}>
        <div className="modal-head">
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div className="avatar">
              {driver.name
                .split(" ")
                .map((n) => n[0])
                .join("")}
            </div>
            <div>
              <h2>{driver.name}</h2>
              <p style={{ margin: 0, fontSize: 13, color: "#64748b" }}>
                {driver.id} · {driver.phone} · {driver.vehicle} ({driver.plate})
              </p>
            </div>
          </div>
          <button className="icon-btn" onClick={close}>
            ✕
          </button>
        </div>

        <div style={{ padding: "16px 24px" }}>
          {/* Status and Active Order strip */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(3, 1fr)",
              gap: 12,
              marginBottom: 16,
            }}
          >
            <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: 12, borderRadius: 8 }}>
              <div style={{ fontSize: 11, color: "#94a3b8", textTransform: "uppercase" }}>Driver Status</div>
              <div style={{ marginTop: 4 }}>
                <Badge>{driver.status}</Badge>
              </div>
            </div>
            <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: 12, borderRadius: 8 }}>
              <div style={{ fontSize: 11, color: "#94a3b8", textTransform: "uppercase" }}>Workload</div>
              <div style={{ marginTop: 4, fontWeight: "bold", color: "#f8fafc", fontSize: 14 }}>
                {driver.workload}
              </div>
            </div>
            <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: 12, borderRadius: 8 }}>
              <div style={{ fontSize: 11, color: "#94a3b8", textTransform: "uppercase" }}>Today's Delivery</div>
              <div style={{ marginTop: 4, fontSize: 13, color: "#f8fafc" }}>
                <span style={{ color: "#22c55e", fontWeight: "bold" }}>{driver.completed} done</span>
                {driver.failed > 0 && <span style={{ color: "#ef4444", marginLeft: 6 }}>· {driver.failed} failed</span>}
              </div>
            </div>
          </div>

          {/* Active Order Card */}
          <div
            style={{
              background: "#0f172a",
              border: "1px solid #1e293b",
              borderRadius: 8,
              padding: 14,
              marginBottom: 16,
            }}
          >
            <div style={{ fontSize: 12, color: "#94a3b8", textTransform: "uppercase", marginBottom: 6 }}>
              Assigned Order (Active Transit)
            </div>
            {driver.order !== "—" ? (
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong
                    style={{ fontSize: 15, color: "#38bdf8", cursor: "pointer", textDecoration: "underline" }}
                    onClick={() => {
                      close()
                      if (openOrder) openOrder(driver.order)
                    }}
                  >
                    {driver.order}
                  </strong>
                  <span style={{ marginLeft: 8 }}><Badge>{driver.orderStatus || "IN_TRANSIT"}</Badge></span>
                  <p style={{ margin: "4px 0 0", fontSize: 12, color: "#94a3b8" }}>
                    Order in active fulfillment. Driver currently navigating along delivery route.
                  </p>
                </div>
                {openOrder && (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      close()
                      openOrder(driver.order)
                    }}
                  >
                    View Order
                  </Button>
                )}
              </div>
            ) : (
              <div style={{ fontSize: 13, color: "#64748b" }}>
                No active delivery assigned to this driver. Driver is currently idle and ready for dispatch.
              </div>
            )}
          </div>

          {/* Real-Time GPS Tracking Card */}
          <div
            style={{
              background: "#0f172a",
              border: "1px solid #334155",
              borderRadius: 10,
              padding: 16,
              position: "relative",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span
                  style={{
                    display: "inline-block",
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    background: driver.hasGps ? (driver.locationStale ? "#f59e0b" : "#22c55e") : "#64748b",
                    boxShadow: driver.hasGps && !driver.locationStale ? "0 0 8px #22c55e" : undefined,
                  }}
                />
                <strong style={{ fontSize: 14, color: "#f8fafc" }}>
                  {driver.hasGps
                    ? (driver.locationStale ? "GPS Signal Stale (>30m threshold)" : "Live GPS Location")
                    : "No GPS Recorded"}
                </strong>
              </div>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>{driver.gpsUpdatedAt || driver.activity}</span>
            </div>

            {driver.locationStale && (
              <div
                style={{
                  background: "rgba(245, 158, 11, 0.1)",
                  border: "1px solid #f59e0b",
                  borderRadius: 6,
                  padding: "8px 12px",
                  fontSize: 12,
                  color: "#fbbf24",
                  marginBottom: 12,
                }}
              >
                ⚠️ Notice: GPS location was updated more than 30 minutes ago. Coordinates may not reflect driver's real-time position.
              </div>
            )}

            {driver.hasGps && driver.latitude && driver.longitude ? (
              <>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 14 }}>
                  <div style={{ background: "#1e293b", padding: 10, borderRadius: 6 }}>
                    <div style={{ fontSize: 11, color: "#94a3b8" }}>LATITUDE / LONGITUDE</div>
                    <div style={{ fontSize: 13, fontFamily: "monospace", color: "#f8fafc", fontWeight: "bold" }}>
                      {driver.latitude.toFixed(6)}° N, {driver.longitude.toFixed(6)}° E
                    </div>
                  </div>
                  <div style={{ background: "#1e293b", padding: 10, borderRadius: 6 }}>
                    <div style={{ fontSize: 11, color: "#94a3b8" }}>SPEED & ACCURACY</div>
                    <div style={{ fontSize: 13, color: "#f8fafc", fontWeight: "bold" }}>
                      {driver.speed || 0} km/h · ±{driver.accuracy || 5}m
                    </div>
                  </div>
                </div>

                {/* SVG Visual Map Preview for Selected Driver */}
                <div
                  style={{
                    height: 180,
                    background: "#090d16",
                    borderRadius: 8,
                    overflow: "hidden",
                    border: "1px solid #1e293b",
                    position: "relative",
                  }}
                >
                  <svg width="100%" height="100%" viewBox="0 0 500 180" style={{ display: "block" }}>
                    <defs>
                      <pattern id="grid" width="25" height="25" patternUnits="userSpaceOnUse">
                        <path d="M 25 0 L 0 0 0 25" fill="none" stroke="#1e293b" strokeWidth="0.5" />
                      </pattern>
                      <linearGradient id="routeGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                        <stop offset="0%" stopColor="#38bdf8" />
                        <stop offset="100%" stopColor="#22c55e" />
                      </linearGradient>
                    </defs>
                    <rect width="100%" height="100%" fill="#090d16" />
                    <rect width="100%" height="100%" fill="url(#grid)" opacity="0.6" />

                    {/* Road paths */}
                    <path d="M 0 90 Q 150 40 250 90 T 500 90" fill="none" stroke="#334155" strokeWidth="6" strokeLinecap="round" />
                    <path d="M 250 0 L 250 180" fill="none" stroke="#334155" strokeWidth="4" />
                    <path d="M 120 180 L 120 0" fill="none" stroke="#1e293b" strokeWidth="3" />
                    <path d="M 380 180 L 380 0" fill="none" stroke="#1e293b" strokeWidth="3" />

                    {/* Active Route if Order assigned */}
                    {driver.order !== "—" && (
                      <>
                        <path
                          d="M 160 85 Q 210 65 250 90 T 360 90"
                          fill="none"
                          stroke="url(#routeGrad)"
                          strokeWidth="3.5"
                          strokeDasharray="6,4"
                        />
                        {/* Destination Pin */}
                        <circle cx="360" cy="90" r="10" fill="#22c55e" fillOpacity="0.2" />
                        <circle cx="360" cy="90" r="5" fill="#22c55e" />
                        <text x="360" y="115" fill="#94a3b8" fontSize="11" textAnchor="middle">
                          Dropoff Point
                        </text>
                      </>
                    )}

                    {/* Driver Vehicle Location Pin */}
                    <circle cx="160" cy="85" r="16" fill="#38bdf8" fillOpacity="0.2">
                      <animate attributeName="r" values="10;20;10" dur="2s" repeatCount="indefinite" />
                    </circle>
                    <circle cx="160" cy="85" r="6" fill="#38bdf8" stroke="#ffffff" strokeWidth="2" />
                    <text x="160" y="62" fill="#38bdf8" fontSize="11" fontWeight="bold" textAnchor="middle">
                      {driver.id} ({driver.name.split(" ")[0]})
                    </text>
                  </svg>
                  <div
                    style={{
                      position: "absolute",
                      bottom: 8,
                      right: 10,
                      background: "rgba(15, 23, 42, 0.85)",
                      padding: "2px 8px",
                      borderRadius: 4,
                      fontSize: 10,
                      color: "#94a3b8",
                      fontFamily: "monospace",
                    }}
                  >
                    GPS: {driver.latitude.toFixed(4)}, {driver.longitude.toFixed(4)}
                  </div>
                </div>
              </>
            ) : (
              <div
                style={{
                  background: "#1e293b",
                  borderRadius: 6,
                  padding: 16,
                  textAlign: "center",
                  color: "#94a3b8",
                  fontSize: 13,
                }}
              >
                No GPS coordinates recorded for this driver. When the driver activates their location in the driver app, live position and transit route will display here.
              </div>
            )}
          </div>
        </div>

        <div className="modal-foot">
          <Button variant="secondary" onClick={() => alert("Connecting dispatch call to " + driver.phone + "...")}>
            Call Driver ({driver.phone})
          </Button>
          <Button variant="primary" onClick={close}>
            Close
          </Button>
        </div>
      </div>
    </div>
  )
}

function DriversPage({ openOrder }: { openOrder?: (id: string) => void }) {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("ALL")
  const [gpsFilter, setGpsFilter] = useState("ALL")
  const [orderFilter, setOrderFilter] = useState("ALL")
  const [page, setPage] = useState(0)
  const [selectedDriver, setSelectedDriver] = useState<DriverMonitoringItem | null>(null)

  const pageSize = 5

  const filtered = drivers.filter((d) => {
    if (search.trim()) {
      const q = search.toLowerCase()
      const match =
        d.name.toLowerCase().includes(q) ||
        d.id.toLowerCase().includes(q) ||
        d.phone.includes(q) ||
        d.plate.toLowerCase().includes(q) ||
        d.order.toLowerCase().includes(q)
      if (!match) return false
    }
    if (statusFilter !== "ALL" && d.status !== statusFilter) return false
    if (gpsFilter === "LIVE" && (!d.hasGps || d.locationStale)) return false
    if (gpsFilter === "STALE" && (!d.hasGps || !d.locationStale)) return false
    if (gpsFilter === "NONE" && d.hasGps) return false
    if (orderFilter === "ACTIVE" && d.order === "—") return false
    if (orderFilter === "IDLE" && d.order !== "—") return false
    return true
  })

  const totalPages = Math.ceil(filtered.length / pageSize) || 1
  const currentPage = Math.min(page, totalPages - 1)
  const pagedList = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize)

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Driver Tracking & Monitoring</h1>
          <p>Real-time location, operational status, and delivery assignments of fleet drivers (PB-046)</p>
        </div>
        <Button icon="download">Export</Button>
      </div>

      <div className="summary-strip driver-strip">
        {[
          ["Available", "138", "success"],
          ["Busy", "284", "blue"],
          ["Offline", "67", "neutral"],
          ["Suspended", "4", "danger"],
          ["Utilization", "72.4%", "purple"],
        ].map(([a, b, c]) => (
          <div key={a}>
            <span className={`summary-icon tone-${c}`}>
              <Icon name="truck" />
            </span>
            <p>
              {a}
              <strong>{b}</strong>
            </p>
          </div>
        ))}
      </div>

      <section className="panel table-panel">
        <div className="filterbar">
          <label className="table-search">
            <Icon name="search" size={16} />
            <input
              placeholder="Search driver name, ID, phone, plate, or order"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </label>

          <select
            className="filter-control"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value)
              setPage(0)
            }}
            style={{ background: "#1e293b", color: "#f8fafc", border: "1px solid #334155", borderRadius: 6, padding: "6px 10px" }}
          >
            <option value="ALL">Status: All</option>
            <option value="AVAILABLE">Available</option>
            <option value="BUSY">Busy</option>
            <option value="OFFLINE">Offline</option>
            <option value="SUSPENDED">Suspended</option>
          </select>

          <select
            className="filter-control hide-small"
            value={gpsFilter}
            onChange={(e) => {
              setGpsFilter(e.target.value)
              setPage(0)
            }}
            style={{ background: "#1e293b", color: "#f8fafc", border: "1px solid #334155", borderRadius: 6, padding: "6px 10px" }}
          >
            <option value="ALL">GPS: All</option>
            <option value="LIVE">Live GPS</option>
            <option value="STALE">Stale GPS (&gt;30m)</option>
            <option value="NONE">No GPS Signal</option>
          </select>

          <select
            className="filter-control hide-small"
            value={orderFilter}
            onChange={(e) => {
              setOrderFilter(e.target.value)
              setPage(0)
            }}
            style={{ background: "#1e293b", color: "#f8fafc", border: "1px solid #334155", borderRadius: 6, padding: "6px 10px" }}
          >
            <option value="ALL">Order: All</option>
            <option value="ACTIVE">With Active Order</option>
            <option value="IDLE">Idle / No Order</option>
          </select>

          {(search || statusFilter !== "ALL" || gpsFilter !== "ALL" || orderFilter !== "ALL") && (
            <Button
              variant="secondary"
              onClick={() => {
                setSearch("")
                setStatusFilter("ALL")
                setGpsFilter("ALL")
                setOrderFilter("ALL")
                setPage(0)
              }}
            >
              Reset
            </Button>
          )}
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Driver</th>
                <th>Status</th>
                <th>Current order</th>
                <th>Real-time GPS Location</th>
                <th>Vehicle & Plate</th>
                <th>Workload</th>
                <th>Today</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {pagedList.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: "center", padding: "32px 16px", color: "#94a3b8" }}>
                    No drivers match the current filters or search query.
                  </td>
                </tr>
              ) : (
                pagedList.map((d) => (
                  <tr key={d.id} style={{ cursor: "pointer" }} onClick={() => setSelectedDriver(d)}>
                    <td>
                      <div className="driver-cell">
                        <div className="avatar small">
                          {d.name
                            .split(" ")
                            .map((n) => n[0])
                            .join("")}
                        </div>
                        <div>
                          <strong>{d.name}</strong>
                          <small>{d.id} · {d.phone}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <Badge>{d.status}</Badge>
                    </td>
                    <td>
                      {d.order !== "—" ? (
                        <div>
                          <span
                            className="link"
                            style={{ fontWeight: "bold" }}
                            onClick={(e) => {
                              e.stopPropagation()
                              if (openOrder) openOrder(d.order)
                            }}
                          >
                            {d.order}
                          </span>
                          <span style={{ marginLeft: 6, fontSize: 11, color: "#94a3b8" }}>
                            ({d.orderStatus || "IN_TRANSIT"})
                          </span>
                        </div>
                      ) : (
                        <span style={{ color: "#64748b" }}>—</span>
                      )}
                    </td>
                    <td>
                      {d.hasGps && d.latitude && d.longitude ? (
                        <div>
                          <span
                            style={{
                              display: "inline-flex",
                              alignItems: "center",
                              gap: 6,
                              fontSize: 12,
                              color: d.locationStale ? "#f59e0b" : "#22c55e",
                              fontWeight: 600,
                            }}
                          >
                            <span
                              style={{
                                width: 8,
                                height: 8,
                                borderRadius: "50%",
                                background: d.locationStale ? "#f59e0b" : "#22c55e",
                              }}
                            />
                            {d.locationStale ? "Stale GPS" : "Live GPS"}
                          </span>
                          <div style={{ fontSize: 11, fontFamily: "monospace", color: "#94a3b8" }}>
                            {d.latitude.toFixed(4)}, {d.longitude.toFixed(4)} ({d.gpsUpdatedAt})
                          </div>
                        </div>
                      ) : (
                        <span style={{ fontSize: 12, color: "#64748b" }}>No GPS signal</span>
                      )}
                    </td>
                    <td>
                      <div>{d.vehicle}</div>
                      <small style={{ color: "#94a3b8" }}>{d.plate}</small>
                    </td>
                    <td>{d.workload}</td>
                    <td>
                      <span style={{ color: "#22c55e", fontWeight: "bold" }}>{d.completed}</span>
                      {d.failed > 0 && <span style={{ color: "#ef4444", marginLeft: 4 }}>/ {d.failed} ✗</span>}
                    </td>
                    <td>
                      <Button
                        variant="secondary"
                        onClick={(e) => {
                          e.stopPropagation()
                          setSelectedDriver(d)
                        }}
                      >
                        Track
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="pagination" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px" }}>
          <span style={{ fontSize: 13, color: "#94a3b8" }}>
            Showing {filtered.length === 0 ? 0 : currentPage * pageSize + 1}–
            {Math.min((currentPage + 1) * pageSize, filtered.length)} of {filtered.length} drivers
          </span>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button
              disabled={currentPage === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              style={{
                background: "#1e293b",
                border: "1px solid #334155",
                color: currentPage === 0 ? "#475569" : "#f8fafc",
                borderRadius: 4,
                padding: "4px 10px",
                cursor: currentPage === 0 ? "not-allowed" : "pointer",
              }}
            >
              ‹ Prev
            </button>
            <span style={{ fontSize: 12, color: "#94a3b8" }}>
              Page {currentPage + 1} / {totalPages}
            </span>
            <button
              disabled={currentPage >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              style={{
                background: "#1e293b",
                border: "1px solid #334155",
                color: currentPage >= totalPages - 1 ? "#475569" : "#f8fafc",
                borderRadius: 4,
                padding: "4px 10px",
                cursor: currentPage >= totalPages - 1 ? "not-allowed" : "pointer",
              }}
            >
              Next ›
            </button>
          </div>
        </div>
      </section>

      {selectedDriver && (
        <DriverDetailModal
          driver={selectedDriver}
          close={() => setSelectedDriver(null)}
          openOrder={openOrder}
        />
      )}
    </div>
  )
}

interface ActionableOrderItem {
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

interface ActionableOrdersData {
  content: ActionableOrderItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  totalActionable: number
  unassignedCount: number
  failedCount: number
}

function ExceptionsPage({
  openOrder,
  onAssignOrder,
  refreshTrigger,
}: {
  openOrder: (id: string) => void
  onAssignOrder?: (id: string, isFailed: boolean) => void
  refreshTrigger?: number
}) {
  const [data, setData] = useState<ActionableOrdersData | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [refreshing, setRefreshing] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const [isAccessDenied, setIsAccessDenied] = useState<boolean>(false)
  const [category, setCategory] = useState<"ALL" | "UNASSIGNED" | "FAILED">("ALL")
  const [search, setSearch] = useState<string>("")
  const [sort, setSort] = useState<"newest_first" | "oldest_first">("newest_first")
  const [page, setPage] = useState<number>(0)
  const size = 10

  const fetchActionableOrders = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true)
    else setLoading(true)
    setError(null)
    setIsAccessDenied(false)

    try {
      const token = localStorage.getItem("token") || "Bearer sample.dispatcher.jwt"
      const params = new URLSearchParams({
        category,
        page: String(page),
        size: String(size),
        sort,
      })
      if (search.trim()) {
        params.append("search", search.trim())
      }

      const res = await fetch(`/api/shipments/assignments/actionable?${params.toString()}`, {
        headers: {
          Accept: "application/json",
          Authorization: token.startsWith("Bearer ") ? token : `Bearer ${token}`,
        },
      })

      if (res.status === 401 || res.status === 403) {
        setIsAccessDenied(true)
        setData(null)
        return
      }

      const body = await res.json()
      if (body.success && body.data) {
        setData(body.data)
      } else {
        setError(body.message || "Failed to load actionable orders")
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err)
      setError(msg || "Network error loading actionable orders")
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [category, page, size, sort, search])

  useEffect(() => {
    fetchActionableOrders(false)
  }, [fetchActionableOrders, refreshTrigger])

  const formatTimeAgo = (iso?: string) => {
    if (!iso) return "—"
    try {
      const diffMs = Date.now() - new Date(iso).getTime()
      const diffMins = Math.floor(diffMs / 60000)
      if (diffMins < 1) return "Just now"
      if (diffMins < 60) return `${diffMins}m ago`
      const diffHours = Math.floor(diffMins / 60)
      if (diffHours < 24) return `${diffHours}h ${diffMins % 60}m ago`
      return `${Math.floor(diffHours / 24)}d ago`
    } catch {
      return iso
    }
  }

  const items = data?.content || []
  const totalActionable = data?.totalActionable ?? 0
  const unassignedCount = data?.unassignedCount ?? 0
  const failedCount = data?.failedCount ?? 0
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 1

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Actionable Orders & Exceptions (PB-049)</h1>
          <p>
            Centralized queue of UNASSIGNED orders and delivery FAILURES requiring Operations intervention
          </p>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <Button icon="refresh" onClick={() => fetchActionableOrders(true)}>
            {refreshing ? "Refreshing..." : "Refresh Queue"}
          </Button>
        </div>
      </div>

      {isAccessDenied && (
        <div
          className="exception-banner"
          style={{ borderColor: "#ef4444", background: "rgba(239, 68, 68, 0.08)" }}
        >
          <div>
            <Icon name="alert" />
            <div>
              <strong style={{ color: "#b91c1c" }}>403 Access Denied: Operations Only</strong>
              <span>
                You do not have permission to view actionable operational orders. Only accounts with DISPATCHER or ADMIN role are authorized.
              </span>
            </div>
          </div>
        </div>
      )}

      {error && !isAccessDenied && (
        <div className="exception-banner" style={{ borderColor: "#f87171" }}>
          <div>
            <Icon name="alert" />
            <div>
              <strong style={{ color: "#dc2626" }}>Error Loading Actionable Orders</strong>
              <span>{error}</span>
            </div>
          </div>
          <Button variant="primary" onClick={() => fetchActionableOrders(false)}>
            Retry
          </Button>
        </div>
      )}

      {!isAccessDenied && (
        <>
          <div
            className="exception-banner"
            style={
              failedCount > 0
                ? { borderColor: "#ef4444", background: "#fef2f2" }
                : unassignedCount > 0
                ? { borderColor: "#f59e0b", background: "#fffbeb" }
                : { borderColor: "#10b981", background: "#f0fdf4" }
            }
          >
            <div>
              <Icon name="alert" />
              <div>
                {failedCount > 0 ? (
                  <>
                    <strong style={{ color: "#dc2626" }}>
                      {failedCount} critical delivery failures require immediate reassignment
                    </strong>
                    <span>
                      {unassignedCount} orders waiting for driver assignment · Total {totalActionable} issues in queue
                    </span>
                  </>
                ) : unassignedCount > 0 ? (
                  <>
                    <strong style={{ color: "#d97706" }}>
                      {unassignedCount} orders currently unassigned awaiting driver dispatch
                    </strong>
                    <span>No delivery failures pending · Review eligible drivers to assign</span>
                  </>
                ) : (
                  <>
                    <strong style={{ color: "#059669" }}>
                      All operational queues clear — no pending exceptions
                    </strong>
                    <span>All orders have active drivers or reached delivered status</span>
                  </>
                )}
              </div>
            </div>
            {failedCount > 0 && (
              <Button
                variant="danger"
                onClick={() => {
                  setCategory("FAILED")
                  setPage(0)
                }}
              >
                Review Failed Only
              </Button>
            )}
          </div>

          <div className="subtabs">
            <button
              className={category === "ALL" ? "active" : ""}
              onClick={() => {
                setCategory("ALL")
                setPage(0)
              }}
            >
              All Attention <span>{totalActionable}</span>
            </button>
            <button
              className={category === "UNASSIGNED" ? "active" : ""}
              onClick={() => {
                setCategory("UNASSIGNED")
                setPage(0)
              }}
            >
              Unassigned Orders <span>{unassignedCount}</span>
            </button>
            <button
              className={category === "FAILED" ? "active" : ""}
              onClick={() => {
                setCategory("FAILED")
                setPage(0)
              }}
            >
              Delivery Failures <span>{failedCount}</span>
            </button>
          </div>

          <section className="panel table-panel">
            <div style={{ display: "flex", gap: 12, padding: "12px 16px", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
              <div style={{ display: "flex", gap: 10, flex: 1, minWidth: 260 }}>
                <label className="table-search" style={{ flex: 1 }}>
                  <Icon name="search" size={16} />
                  <input
                    placeholder="Search by Order ID, Address, Recipient, Reason..."
                    value={search}
                    onChange={(e) => {
                      setSearch(e.target.value)
                      setPage(0)
                    }}
                  />
                </label>
              </div>

              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <span style={{ fontSize: 12, color: "#6b7280" }}>Sort:</span>
                <select
                  value={sort}
                  onChange={(e) => {
                    setSort(e.target.value as "newest_first" | "oldest_first")
                    setPage(0)
                  }}
                  style={{
                    padding: "6px 10px",
                    borderRadius: 6,
                    border: "1px solid #d1d5db",
                    fontSize: 12,
                    background: "#fff",
                  }}
                >
                  <option value="newest_first">Newest First</option>
                  <option value="oldest_first">Oldest / Waiting Longest First</option>
                </select>
              </div>
            </div>

            {loading ? (
              <div style={{ padding: "48px 16px", textAlign: "center", color: "#6b7280" }}>
                <div style={{ display: "inline-block", animation: "spin 1s linear infinite", marginBottom: 8 }}>
                  <Icon name="refresh" size={24} />
                </div>
                <p>Loading actionable orders...</p>
              </div>
            ) : items.length === 0 ? (
              <div style={{ padding: "48px 16px", textAlign: "center", color: "#6b7280" }}>
                <Icon name="check" size={32} />
                <h3 style={{ marginTop: 8, color: "#374151" }}>No Actionable Orders Found</h3>
                <p style={{ fontSize: 13, color: "#6b7280", marginTop: 4 }}>
                  {search
                    ? `No orders matching "${search}". Try adjusting your search keyword.`
                    : category !== "ALL"
                    ? `No orders currently in ${category} category requiring attention.`
                    : "All orders are operating smoothly without pending exceptions."}
                </p>
              </div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Order ID</th>
                      <th>Category</th>
                      <th>Severity</th>
                      <th>Age / Waiting</th>
                      <th>Customer & Destination</th>
                      <th>Failure / Issue Reason</th>
                      <th>Previous Driver</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((x) => {
                      const isFail = x.status === "FAILED"
                      return (
                        <tr key={x.orderId} className={isFail ? "overdue" : ""}>
                          <td>
                            <strong
                              className="link"
                              onClick={() => openOrder(x.orderId)}
                              style={{ cursor: "pointer", color: "#2563eb" }}
                            >
                              {x.orderNumber || x.orderId}
                            </strong>
                            <div style={{ fontSize: 10, color: "#9ca3af" }}>
                              {x.orderId}
                            </div>
                          </td>
                          <td>
                            <Badge tone={isFail ? "urgent" : "warning"}>
                              {x.status}
                            </Badge>
                          </td>
                          <td>
                            <span
                              style={{
                                display: "inline-block",
                                padding: "2px 6px",
                                borderRadius: 4,
                                fontSize: 10,
                                fontWeight: 700,
                                background: isFail ? "#fee2e2" : "#fef3c7",
                                color: isFail ? "#dc2626" : "#d97706",
                              }}
                            >
                              {x.issueSeverity || (isFail ? "CRITICAL" : "HIGH")}
                            </span>
                          </td>
                          <td>
                            <div>{formatTimeAgo(isFail ? x.failedAt : x.unassignedAt || x.createdAt)}</div>
                            <small style={{ color: "#9ca3af", fontSize: 10 }}>
                              Created {formatTimeAgo(x.createdAt)}
                            </small>
                          </td>
                          <td>
                            <strong style={{ fontSize: 12 }}>{x.recipientName || x.customerId}</strong>
                            <div style={{ fontSize: 11, color: "#6b7280", maxWidth: 220, textOverflow: "ellipsis", overflow: "hidden", whiteSpace: "nowrap" }}>
                              {x.deliveryAddress}
                            </div>
                          </td>
                          <td>
                            {isFail ? (
                              <div style={{ color: "#dc2626" }}>
                                <strong>{x.failureReason || "Delivery failure"}</strong>
                                {x.failureNotes && (
                                  <div style={{ fontSize: 11, color: "#7f1d1d" }}>{x.failureNotes}</div>
                                )}
                              </div>
                            ) : (
                              <div style={{ color: "#d97706" }}>
                                <span>{x.unassignedReason || "Awaiting driver assignment"}</span>
                              </div>
                            )}
                          </td>
                          <td>
                            <span style={{ fontSize: 12 }}>
                              {x.previousDriverId || x.driverId || "—"}
                            </span>
                          </td>
                          <td style={{ textAlign: "right" }}>
                            <div style={{ display: "inline-flex", gap: 6, justifyContent: "flex-end" }}>
                              {isFail ? (
                                <Button
                                  variant="danger"
                                  icon="route"
                                  onClick={() =>
                                    onAssignOrder
                                      ? onAssignOrder(x.orderId, true)
                                      : openOrder(x.orderId)
                                  }
                                >
                                  Reassign
                                </Button>
                              ) : (
                                <Button
                                  variant="primary"
                                  icon="route"
                                  onClick={() =>
                                    onAssignOrder
                                      ? onAssignOrder(x.orderId, false)
                                      : openOrder(x.orderId)
                                  }
                                >
                                  Assign
                                </Button>
                              )}
                              <button
                                className="icon-btn"
                                title="View Order Details"
                                onClick={() => openOrder(x.orderId)}
                                style={{ padding: "4px 8px", cursor: "pointer", border: "1px solid #d1d5db", borderRadius: 4, background: "#f9fafb" }}
                              >
                                👁
                              </button>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {!loading && totalElements > 0 && (
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  padding: "12px 16px",
                  borderTop: "1px solid #e5e7eb",
                  fontSize: 12,
                  color: "#6b7280",
                }}
              >
                <span>
                  Showing {page * size + 1}–{Math.min((page + 1) * size, totalElements)} of {totalElements} orders requiring attention
                </span>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <button
                    className="btn btn-secondary"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    style={{ opacity: page === 0 ? 0.5 : 1, padding: "4px 10px", fontSize: 12 }}
                  >
                    Previous
                  </button>
                  <span>
                    Page {page + 1} of {totalPages}
                  </span>
                  <button
                    className="btn btn-secondary"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                    style={{ opacity: page >= totalPages - 1 ? 0.5 : 1, padding: "4px 10px", fontSize: 12 }}
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}

function ReportsPage() {
  const metrics = [
    ["Total orders", "68,492", "+7.8%"],
    ["Completed", "64,180", "+8.1%"],
    ["Failed", "1,204", "-0.6%"],
    ["Cancellation rate", "1.9%", "-0.2%"],
    ["Assignment success", "95.4%", "+1.1%"],
    ["Avg. assignment", "1m 42s", "-12s"],
    ["Avg. delivery", "42m 18s", "-1m 08s"],
    ["Driver utilization", "74.6%", "+2.4%"],
  ]
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Reports</h1>
          <p>Operational performance and delivery service metrics</p>
        </div>
        <div className="report-actions">
          <button className="date-range">
            <Icon name="clock" size={15} /> May 1 – May 19, 2024 ⌄
          </button>
          <Button icon="download">Export report</Button>
        </div>
      </div>
      <div className="report-metrics">
        {metrics.map(([a, b, c]) => (
          <div key={a}>
            <span>{a}</span>
            <strong>{b}</strong>
            <small
              className={
                c.startsWith("-") && a === "Failed"
                  ? "positive"
                  : c.startsWith("-")
                    ? "positive"
                    : ""
              }
            >
              {c} vs prior period
            </small>
          </div>
        ))}
      </div>
      <div className="dashboard-grid report-grid">
        <section className="panel chart-panel">
          <SectionTitle
            title="Order volume & completion"
            meta="Daily performance"
          />
          <div className="line-chart">
            <div className="grid-lines">
              <i />
              <i />
              <i />
              <i />
            </div>
            <svg viewBox="0 0 600 180" preserveAspectRatio="none">
              <path
                className="area"
                d="M0 155 C45 140 58 90 110 108 S175 145 220 90 S285 56 330 78 S400 40 445 70 S515 115 600 35 L600 180 L0 180Z"
              />
              <path
                className="line"
                d="M0 155 C45 140 58 90 110 108 S175 145 220 90 S285 56 330 78 S400 40 445 70 S515 115 600 35"
              />
            </svg>
            <div className="x-axis">
              <span>May 1</span>
              <span>May 5</span>
              <span>May 9</span>
              <span>May 13</span>
              <span>May 17</span>
            </div>
          </div>
        </section>
        <section className="panel health-panel">
          <SectionTitle title="SLA performance" meta="Current period" />
          <div className="health-list">
            {[
              ["Assignment under 3 min", "95.4%", "Target 95%", "success", 95],
              ["Pickup under 15 min", "89.2%", "Target 90%", "warning", 89],
              ["Delivery on time", "92.1%", "Target 90%", "success", 92],
              [
                "Exceptions resolved under 30 min",
                "86.7%",
                "Target 85%",
                "success",
                87,
              ],
            ].map(([a, b, c, t, w]) => (
              <div className="health-row" key={a}>
                <div>
                  <span>{a}</span>
                  <strong>{b}</strong>
                </div>
                <div className="progress">
                  <i className={`progress-${t}`} style={{ width: `${w}%` }} />
                </div>
                <small>{c}</small>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}

function SettingsPage() {
  return (
    <div className="page settings-page">
      <div className="page-title">
        <div>
          <h1>Settings</h1>
          <p>Configure operational rules and access</p>
        </div>
      </div>
      <div className="settings-layout">
        <div className="settings-nav">
          {[
            "Assignment configuration",
            "Delivery statuses",
            "Notifications",
            "Operator accounts",
            "Roles & permissions",
          ].map((x, i) => (
            <button className={i === 0 ? "active" : ""} key={x}>
              <Icon
                name={
                  i === 0
                    ? "route"
                    : i === 1
                      ? "box"
                      : i === 2
                        ? "bell"
                        : i === 3
                          ? "user"
                          : "settings"
                }
              />
              {x}
              <Icon name="chevron" size={15} />
            </button>
          ))}
        </div>
        <section className="panel settings-content">
          <SectionTitle
            title="Assignment configuration"
            meta="Rules used by the automatic driver assignment engine"
          />
          <div className="setting-group">
            <h3>Assignment engine</h3>
            <label className="switch-row">
              <div>
                <strong>Automatic driver assignment</strong>
                <span>
                  Automatically find and assign eligible drivers to new orders
                </span>
              </div>
              <input type="checkbox" defaultChecked />
              <i />
            </label>
            <div className="form-grid">
              <label>
                Maximum assignment attempts
                <select defaultValue="3">
                  <option>3 attempts</option>
                </select>
              </label>
              <label>
                Driver response timeout
                <select defaultValue="60">
                  <option>60 seconds</option>
                </select>
              </label>
              <label>
                Maximum pickup radius
                <select defaultValue="8">
                  <option>8 kilometers</option>
                </select>
              </label>
              <label>
                Retry interval
                <select defaultValue="30">
                  <option>30 seconds</option>
                </select>
              </label>
            </div>
          </div>
          <div className="setting-group">
            <h3>Candidate prioritization</h3>
            {[
              [
                "Distance from pickup",
                "Prioritize drivers closest to the pickup location",
              ],
              [
                "Current workload",
                "Prefer drivers with fewer active or queued orders",
              ],
              [
                "Driver acceptance rate",
                "Factor recent acceptance performance into ranking",
              ],
            ].map((x, i) => (
              <label className="check-row" key={x[0]}>
                <input type="checkbox" defaultChecked={i < 2} />
                <div>
                  <strong>{x[0]}</strong>
                  <span>{x[1]}</span>
                </div>
              </label>
            ))}
          </div>
          <div className="settings-footer">
            <span>Last updated by Alex Kim · May 16, 2024</span>
            <Button variant="primary">Save changes</Button>
          </div>
        </section>
      </div>
    </div>
  )
}

function OrderDetail({
  id,
  back,
  assign,
  track,
}: {
  id: string
  back: () => void
  assign: () => void
  track: () => void
}) {
  const failed = id.endsWith("8842") || id.endsWith("8818")
  return (
    <div className="page order-detail">
      <button className="back-button" onClick={back}>
        ← Back to orders
      </button>
      <div className="detail-header">
        <div>
          <div>
            <h1>{id}</h1>
            <Badge>{failed ? "ASSIGNMENT FAILED" : "IN_TRANSIT"}</Badge>
            <Badge tone="urgent">Urgent</Badge>
          </div>
          <p>Created today at 10:42 · Express delivery</p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Button>Cancel order</Button>
          {!failed && (
            <Button variant="primary" icon="pin" onClick={track}>
              Track Driver GPS
            </Button>
          )}
          {failed ? (
            <Button variant="primary" icon="route" onClick={assign}>
              Reassign driver
            </Button>
          ) : (
            <Button icon="route" onClick={assign}>
              Reassign driver
            </Button>
          )}
        </div>
      </div>
      {failed && (
        <div className="failure-banner">
          <div>
            <Icon name="alert" />
            <div>
              <strong>Order Delivery Failed — Dispatcher Action Required</strong>
              <span>
                Failure reason: Recipient unavailable at delivery address. Current driver was released; please select a replacement eligible driver to reassign and resume delivery.
              </span>
            </div>
          </div>
          <Button variant="danger" icon="route" onClick={assign}>
            Reassign driver
          </Button>
        </div>
      )}
      <div className="detail-grid">
        <div className="detail-main">
          <section className="panel timeline-panel">
            <SectionTitle title="Delivery status" meta="Live order lifecycle" />
            <div className="timeline">
              {[
                "Created",
                "Processing",
                "Assigning",
                "Assigned",
                "Picked up",
                "In transit",
                "Delivered",
              ].map((x, i) => (
                <div
                  className={i < 2 ? "done" : i === 2 ? "current" : ""}
                  key={x}
                >
                  <i>{i < 2 ? <Icon name="check" size={13} /> : i + 1}</i>
                  <span>
                    {x}
                    <small>
                      {i === 0
                        ? "10:42:08"
                        : i === 1
                          ? "10:42:12"
                          : i === 2
                            ? "In progress"
                            : "Pending"}
                    </small>
                  </span>
                </div>
              ))}
            </div>
          </section>
          <section className="panel">
            <SectionTitle title="Route & delivery details" />
            <div className="route-details">
              <div className="route-line">
                <i className="pickup-dot" />
                <div>
                  <span>PICKUP</span>
                  <strong>Westgate Mall, Loading Bay B</strong>
                  <p>3 Gateway Drive, Singapore 608532</p>
                  <small>Contact: Ken Low · +65 9123 4408</small>
                </div>
              </div>
              <div className="route-line">
                <i className="drop-dot" />
                <div>
                  <span>DELIVERY</span>
                  <strong>18 Boon Lay Way, #04-112</strong>
                  <p>TradeHub 21, Singapore 609966</p>
                  <small>Contact: Maya Chen · +65 8891 2042</small>
                </div>
              </div>
            </div>
            <div className="detail-meta">
              <div>
                <span>Distance</span>
                <strong>6.8 km</strong>
              </div>
              <div>
                <span>Estimated duration</span>
                <strong>32 minutes</strong>
              </div>
              <div>
                <span>Estimated delivery</span>
                <strong>11:55</strong>
              </div>
            </div>
          </section>
          <section className="panel">
            <SectionTitle
              title="Assignment history"
              meta="Automatic assignment attempts and outcomes"
            />
            <div className="history-table">
              <div className="history-head">
                <span>Attempt</span>
                <span>Driver considered</span>
                <span>Result</span>
                <span>Reason</span>
                <span>Timestamp</span>
              </div>
              {[
                [
                  "#3",
                  "Alvin Teo · DRV-1210",
                  "Timed out",
                  "No response",
                  "10:50:31",
                ],
                [
                  "#2",
                  "Thu Nguyen · DRV-0881",
                  "Rejected",
                  "Vehicle capacity",
                  "10:47:18",
                ],
                [
                  "#1",
                  "Sofia Malik · DRV-1009",
                  "Skipped",
                  "Became unavailable",
                  "10:44:02",
                ],
              ].map((r) => (
                <div key={r[0]}>
                  {r.map((c, i) => (
                    <span key={c}>{i === 2 ? <Badge>{c}</Badge> : c}</span>
                  ))}
                </div>
              ))}
            </div>
          </section>
        </div>
        <aside className="detail-side">
          <section className="panel">
            <SectionTitle title="Customer" />
            <div className="profile-row">
              <div className="avatar">MC</div>
              <div>
                <strong>Maya Chen</strong>
                <span>+65 8891 2042</span>
                <span>maya.chen@example.com</span>
              </div>
            </div>
          </section>
          <section className="panel">
            <SectionTitle title="Package" />
            <dl>
              <div>
                <dt>Type</dt>
                <dd>Parcel</dd>
              </div>
              <div>
                <dt>Weight</dt>
                <dd>4.2 kg</dd>
              </div>
              <div>
                <dt>Dimensions</dt>
                <dd>40 × 28 × 24 cm</dd>
              </div>
              <div>
                <dt>Handling</dt>
                <dd>
                  <Badge tone="warning">Fragile</Badge>
                </dd>
              </div>
            </dl>
          </section>
          <section className="panel system-log">
            <SectionTitle title="Recent activity" />
            {[
              ["10:51:31", "Exception created", "Assignment timeout"],
              ["10:50:31", "Attempt 3 timed out", "No driver response"],
              ["10:47:18", "Driver rejected", "Vehicle capacity"],
              ["10:42:12", "Processing started", "System workflow"],
            ].map((x) => (
              <div key={x[0]}>
                <i />
                <span>
                  <strong>{x[1]}</strong>
                  <small>
                    {x[0]} · {x[2]}
                  </small>
                </span>
              </div>
            ))}
          </section>
        </aside>
      </div>
    </div>
  )
}

function AssignModal({
  close,
  orderId,
  isFailed = false,
  onSuccess,
}: {
  close: () => void
  orderId?: string | null
  isFailed?: boolean
  onSuccess?: () => void
}) {
  const [selected, setSelected] = useState("DRV-0881")
  const [submitting, setSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const handleAssign = async () => {
    if (!orderId) {
      close()
      return
    }
    setSubmitting(true)
    setErrorMsg(null)
    setSuccessMsg(null)

    try {
      const token = localStorage.getItem("token") || "Bearer sample.dispatcher.jwt"
      const url = isFailed
        ? `/api/shipments/assignments/${orderId}/reassign`
        : `/api/shipments/assignments/${orderId}/manual-assign`

      const payload = isFailed
        ? { newDriverId: selected, reason: "Operations reassign from actionable dashboard" }
        : { driverId: selected, notes: "Operations manual assign from actionable dashboard" }

      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          Authorization: token.startsWith("Bearer ") ? token : `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      })

      const data = await res.json()

      if (res.status === 409) {
        setErrorMsg("Order was already assigned by another operator. Please refresh your list.")
        return
      }

      if (!res.ok || !data.success) {
        setErrorMsg(data.message || "Failed to process driver assignment. State may have changed.")
        return
      }

      setSuccessMsg(`Driver ${selected} successfully ${isFailed ? "reassigned" : "assigned"}!`)
      if (onSuccess) onSuccess()
      setTimeout(() => {
        close()
      }, 700)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err)
      setErrorMsg(msg || "Network error while assigning driver.")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal">
        <div className="modal-head">
          <div>
            <h2>{isFailed ? "Reassign driver for failed order" : "Assign driver manually"}</h2>
            <p>Order {orderId || "SXP-240519-8818"} · Dispatch queue</p>
          </div>
          <button className="icon-button" onClick={close}>
            <Icon name="close" />
          </button>
        </div>

        {errorMsg && (
          <div
            className="modal-notice"
            style={{ background: "#fef2f2", borderColor: "#ef4444", color: "#b91c1c" }}
          >
            <Icon name="alert" size={17} />
            <span>{errorMsg}</span>
          </div>
        )}

        {successMsg && (
          <div
            className="modal-notice"
            style={{ background: "#f0fdf4", borderColor: "#10b981", color: "#047857" }}
          >
            <Icon name="check" size={17} />
            <span>{successMsg}</span>
          </div>
        )}

        <div className="modal-notice">
          <Icon name="alert" size={17} />
          <span>
            {isFailed
              ? "Reassigning this failed order will release previous driver, reset delivery state, and assign the selected eligible driver."
              : "Manual assignment overrides the automatic assignment workflow. The selected driver will receive an immediate assignment notification."}
          </span>
        </div>
        <div className="modal-tools">
          <label className="table-search">
            <Icon name="search" size={16} />
            <input placeholder="Search available drivers" />
          </label>
          <button className="filter-control">
            Vehicle <span>All</span>
            <span>⌄</span>
          </button>
        </div>
        <div className="driver-options">
          {[
            [
              "DRV-0881",
              "Thu Nguyen",
              "AVAILABLE",
              "0 active",
              "1.8 km",
              "Car",
              "4.9",
            ],
            [
              "DRV-1210",
              "Alvin Teo",
              "AVAILABLE",
              "1 queued",
              "2.4 km",
              "Van",
              "4.8",
            ],
            [
              "DRV-1048",
              "Danish Rahman",
              "BUSY",
              "2 active",
              "1.2 km",
              "Motorcycle",
              "4.9",
            ],
            [
              "DRV-0732",
              "Jay Kumar",
              "OFFLINE",
              "0 active",
              "3.1 km",
              "Motorcycle",
              "4.6",
            ],
          ].map((d) => (
            <button
              className={`${selected === d[0] ? "selected" : ""} ${
                d[2] !== "AVAILABLE" ? "disabled" : ""
              }`}
              key={d[0]}
              onClick={() => d[2] === "AVAILABLE" && setSelected(d[0])}
            >
              <i className="radio" />
              <div className="avatar small">
                {d[1]
                  .split(" ")
                  .map((n) => n[0])
                  .join("")}
              </div>
              <div className="driver-option-main">
                <strong>
                  {d[1]} <small>{d[0]}</small>
                </strong>
                <span>
                  {d[5]} · ★ {d[6]} rating
                </span>
              </div>
              <div>
                <span>Availability</span>
                <Badge>{d[2]}</Badge>
              </div>
              <div>
                <span>Workload</span>
                <strong>{d[3]}</strong>
              </div>
              <div>
                <span>From pickup</span>
                <strong>{d[4]}</strong>
              </div>
            </button>
          ))}
        </div>
        <div className="modal-foot">
          <span>2 of 4 drivers are eligible for assignment</span>
          <div>
            <Button onClick={close}>Cancel</Button>
            <Button
              variant={isFailed ? "danger" : "primary"}
              onClick={handleAssign}
            >
              {submitting
                ? "Processing..."
                : isFailed
                ? "Reassign selected driver"
                : "Assign selected driver"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function TrackDriverModal({
  orderId,
  close,
}: {
  orderId: string
  close: () => void
}) {
  const [copied, setCopied] = useState(false)
  const [showDirections, setShowDirections] = useState(false)
  const isFailed = orderId.endsWith("8842") || orderId.endsWith("8818")

  return (
    <div className="modal-backdrop">
      <div className="modal" style={{ maxWidth: 640 }}>
        <div className="modal-head">
          <div>
            <h2>Live Driver Tracking: {orderId}</h2>
            <p>Real-time GPS coordinates of assigned driver for this order</p>
          </div>
          <button className="icon-btn" onClick={close}>
            ✕
          </button>
        </div>

        {isFailed ? (
          <div className="modal-notice" style={{ background: "rgba(239, 68, 68, 0.1)", borderColor: "#ef4444" }}>
            <Icon name="alert" />
            <div>
              <strong>Tracking Not Available</strong>
              <span>This order has failed delivery or driver has been released. Live tracking is inactive.</span>
            </div>
          </div>
        ) : (
          <div style={{ padding: "16px 24px" }}>
            <div
              style={{
                background: "#0f172a",
                borderRadius: 12,
                padding: 16,
                marginBottom: 16,
                border: "1px solid #334155",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                <span
                  style={{
                    fontSize: 12,
                    color: "#22c55e",
                    display: "flex",
                    alignItems: "center",
                    gap: 6,
                    fontWeight: 600,
                  }}
                >
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: "50%",
                      background: "#22c55e",
                      display: "inline-block",
                    }}
                  ></span>
                  LIVE GPS FIX · Active Delivery
                </span>
                <span style={{ fontSize: 12, color: "#94a3b8" }}>Last ping: 2 mins ago (Fresh)</span>
              </div>

              {/* Vector Map Preview */}
              <svg
                width="100%"
                height="160"
                viewBox="0 0 500 160"
                style={{ background: "#1e293b", borderRadius: 8 }}
              >
                <defs>
                  <linearGradient id="routeGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor="#3b82f6" />
                    <stop offset="60%" stopColor="#10b981" />
                    <stop offset="100%" stopColor="#94a3b8" />
                  </linearGradient>
                </defs>
                <path
                  d="M 60 110 Q 180 30, 290 85 T 440 50"
                  fill="none"
                  stroke="url(#routeGrad)"
                  strokeWidth="4"
                  strokeDasharray="6 4"
                />
                <circle cx="60" cy="110" r="8" fill="#3b82f6" />
                <text x="35" y="135" fill="#93c5fd" fontSize="11" fontWeight="600">
                  Hub (Pickup)
                </text>
                <circle cx="290" cy="85" r="14" fill="rgba(16, 185, 129, 0.3)" />
                <circle cx="290" cy="85" r="8" fill="#10b981" />
                <text x="255" y="70" fill="#6ee7b7" fontSize="12" fontWeight="bold">
                  🚚 Driver (28.5 km/h)
                </text>
                <circle cx="440" cy="50" r="8" fill="#f59e0b" />
                <text x="390" y="35" fill="#fcd34d" fontSize="11" fontWeight="600">
                  Customer Dest
                </text>
              </svg>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 12,
                  marginTop: 14,
                }}
              >
                <div style={{ background: "#1e293b", padding: 10, borderRadius: 8 }}>
                  <div style={{ fontSize: 11, color: "#94a3b8" }}>CURRENT COORDINATES</div>
                  <div
                    style={{
                      fontSize: 14,
                      fontWeight: "bold",
                      color: "#f8fafc",
                      fontFamily: "monospace",
                    }}
                  >
                    21.028511° N, 105.854444° E
                  </div>
                </div>
                <div style={{ background: "#1e293b", padding: 10, borderRadius: 8 }}>
                  <div style={{ fontSize: 11, color: "#94a3b8" }}>STATUS & ACCURACY</div>
                  <div style={{ fontSize: 14, fontWeight: "bold", color: "#f8fafc" }}>
                    IN_TRANSIT · ± 4.5m
                  </div>
                </div>
              </div>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                fontSize: 13,
                color: "#64748b",
                marginBottom: 16,
              }}
            >
              <span>
                Assigned Driver: <strong>T. Nguyen (DRV-0821)</strong>
              </span>
              <button
                className="btn btn-secondary"
                style={{ padding: "4px 10px", fontSize: 12 }}
                onClick={() => {
                  navigator.clipboard?.writeText("21.028511, 105.854444")
                  setCopied(true)
                  setTimeout(() => setCopied(false), 2000)
                }}
              >
                {copied ? "✓ Copied GPS" : "Copy Coordinates"}
              </button>
            </div>

            {/* Map Service Directions & Routing (PB-047) */}
            <div
              style={{
                background: "#0f172a",
                borderRadius: 12,
                padding: 14,
                border: "1px solid #334155",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  cursor: "pointer",
                }}
                onClick={() => setShowDirections(!showDirections)}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 16 }}>🗺️</span>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: "bold", color: "#f8fafc" }}>
                      Map Service Direction & ETA
                    </div>
                    <div style={{ fontSize: 11, color: "#94a3b8" }}>
                      Distance: <strong>3.8 km</strong> · Est. Time: <strong>~11 mins</strong> (Motorcycle)
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span
                    style={{
                      fontSize: 10,
                      padding: "2px 8px",
                      borderRadius: 12,
                      background: "rgba(59, 130, 246, 0.2)",
                      color: "#60a5fa",
                      fontWeight: 600,
                    }}
                  >
                    OSRM / MOCK
                  </span>
                  <span style={{ fontSize: 12, color: "#94a3b8" }}>
                    {showDirections ? "▲ Hide Steps" : "▼ Show Steps"}
                  </span>
                </div>
              </div>

              {showDirections && (
                <div style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid #1e293b" }}>
                  <div style={{ fontSize: 11, fontWeight: "bold", color: "#94a3b8", marginBottom: 8 }}>
                    TURN-BY-TURN NAVIGATION INSTRUCTIONS:
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    <div style={{ display: "flex", gap: 10, fontSize: 12 }}>
                      <span style={{ color: "#3b82f6", fontWeight: "bold" }}>1.</span>
                      <div>
                        <div style={{ color: "#e2e8f0" }}>Depart from current GPS position toward Ring Road 3</div>
                        <div style={{ fontSize: 11, color: "#64748b" }}>750m · ~2 mins · Maneuver: Depart</div>
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 10, fontSize: 12 }}>
                      <span style={{ color: "#3b82f6", fontWeight: "bold" }}>2.</span>
                      <div>
                        <div style={{ color: "#e2e8f0" }}>Continue straight on Le Van Luong arterial avenue</div>
                        <div style={{ fontSize: 11, color: "#64748b" }}>2.1 km · ~6 mins · Maneuver: Straight</div>
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 10, fontSize: 12 }}>
                      <span style={{ color: "#3b82f6", fontWeight: "bold" }}>3.</span>
                      <div>
                        <div style={{ color: "#e2e8f0" }}>Turn right onto destination residential alley</div>
                        <div style={{ fontSize: 11, color: "#64748b" }}>650m · ~2 mins · Maneuver: Turn Right</div>
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 10, fontSize: 12 }}>
                      <span style={{ color: "#22c55e", fontWeight: "bold" }}>4.</span>
                      <div>
                        <div style={{ color: "#e2e8f0" }}>Arrive at customer delivery destination</div>
                        <div style={{ fontSize: 11, color: "#64748b" }}>300m · ~1 min · Maneuver: Arrive</div>
                      </div>
                    </div>
                  </div>
                  <div
                    style={{
                      marginTop: 10,
                      padding: "6px 10px",
                      borderRadius: 6,
                      background: "rgba(16, 185, 129, 0.1)",
                      border: "1px solid rgba(16, 185, 129, 0.2)",
                      fontSize: 11,
                      color: "#10b981",
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                    }}
                  >
                    <span>✓</span>
                    <span>Resilient routing active: automatic Haversine fallback guaranteed during provider downtime.</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        <div className="modal-foot">
          <Button onClick={close}>Close</Button>
        </div>
      </div>
    </div>
  )
}

export default function App() {
  const [page, setPage] = useState<Page>("Dashboard")
  const [order, setOrder] = useState<string | null>(null)
  const [modal, setModal] = useState(false)
  const [trackModal, setTrackModal] = useState(false)
  const [assignOrderId, setAssignOrderId] = useState<string | null>(null)
  const [assignIsFailed, setAssignIsFailed] = useState<boolean>(false)
  const [refreshTrigger, setRefreshTrigger] = useState(0)

  const openOrder = (id: string) => setOrder(id)

  const handleOpenAssign = (id: string, isFailed: boolean) => {
    setAssignOrderId(id)
    setAssignIsFailed(isFailed)
    setModal(true)
  }

  let content: ReactNode
  if (order)
    content = (
      <OrderDetail
        id={order}
        back={() => setOrder(null)}
        assign={() => handleOpenAssign(order, order.endsWith("8842") || order.endsWith("8818"))}
        track={() => setTrackModal(true)}
      />
    )
  else if (page === "Dashboard")
    content = (
      <Dashboard
        openOrder={openOrder}
        goExceptions={() => setPage("Exceptions")}
      />
    )
  else if (page === "Orders") content = <OrdersPage openOrder={openOrder} />
  else if (page === "Assignment")
    content = (
      <AssignmentPage
        openModal={() => handleOpenAssign("SXP-240519-8842", true)}
        openOrder={openOrder}
      />
    )
  else if (page === "Drivers") content = <DriversPage openOrder={openOrder} />
  else if (page === "Exceptions")
    content = (
      <ExceptionsPage
        openOrder={openOrder}
        onAssignOrder={handleOpenAssign}
        refreshTrigger={refreshTrigger}
      />
    )
  else if (page === "Reports") content = <ReportsPage />
  else content = <SettingsPage />

  return (
    <AppShell
      page={page}
      setPage={(p) => {
        setPage(p)
        setOrder(null)
      }}
    >
      {content}
      {modal && (
        <AssignModal
          close={() => {
            setModal(false)
            setAssignOrderId(null)
          }}
          orderId={assignOrderId || order}
          isFailed={assignIsFailed}
          onSuccess={() => setRefreshTrigger((prev) => prev + 1)}
        />
      )}
      {trackModal && order && (
        <TrackDriverModal close={() => setTrackModal(false)} orderId={order} />
      )}
    </AppShell>
  )
}

