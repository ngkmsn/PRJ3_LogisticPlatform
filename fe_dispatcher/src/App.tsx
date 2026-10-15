import { useState, type ReactNode } from "react"

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

function Icon({ name, size = 18 }: { name: IconName size?: number }) {
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

const drivers = [
  {
    id: "DRV-1048",
    name: "Danish Rahman",
    status: "BUSY",
    order: "SXP-240519-8839",
    workload: "2 active",
    vehicle: "Motorcycle",
    activity: "1m ago",
    completed: 12,
    failed: 0,
  },
  {
    id: "DRV-0923",
    name: "Li Wei",
    status: "BUSY",
    order: "SXP-240519-8831",
    workload: "1 active",
    vehicle: "Van",
    activity: "2m ago",
    completed: 8,
    failed: 0,
  },
  {
    id: "DRV-1155",
    name: "Farid Ismail",
    status: "BUSY",
    order: "SXP-240519-8812",
    workload: "2 active",
    vehicle: "Motorcycle",
    activity: "3m ago",
    completed: 10,
    failed: 1,
  },
  {
    id: "DRV-0881",
    name: "Thu Nguyen",
    status: "AVAILABLE",
    order: "—",
    workload: "0 active",
    vehicle: "Car",
    activity: "Just now",
    completed: 9,
    failed: 0,
  },
  {
    id: "DRV-1210",
    name: "Alvin Teo",
    status: "AVAILABLE",
    order: "—",
    workload: "1 queued",
    vehicle: "Van",
    activity: "1m ago",
    completed: 7,
    failed: 0,
  },
  {
    id: "DRV-0732",
    name: "Jay Kumar",
    status: "OFFLINE",
    order: "—",
    workload: "0 active",
    vehicle: "Motorcycle",
    activity: "14m ago",
    completed: 6,
    failed: 2,
  },
  {
    id: "DRV-1009",
    name: "Sofia Malik",
    status: "SUSPENDED",
    order: "—",
    workload: "0 active",
    vehicle: "Car",
    activity: "2d ago",
    completed: 0,
    failed: 0,
  },
]

function Badge({ children, tone }: { children: ReactNode tone?: string }) {
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

function Dashboard({
  openOrder,
  goExceptions,
}: {
  openOrder: (id: string) => void
  goExceptions: () => void
}) {
  const metrics = [
    ["Total orders today", "2,431", "+8.2%", "vs yesterday", "neutral"],
    ["Waiting processing", "84", "+12", "last hour", "warning"],
    ["Being assigned", "31", "94.8%", "auto success", "blue"],
    ["In delivery", "612", "25.2%", "of today's orders", "purple"],
    ["Delivered", "1,647", "+6.4%", "vs yesterday", "success"],
    ["Failed / exception", "57", "2.3%", "of today's orders", "danger"],
    ["Needs intervention", "12", "5 critical", "action required", "danger"],
  ]
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Operations dashboard</h1>
          <p>
            Live overview of today's delivery operation · Sunday, 19 May 2024
          </p>
        </div>
        <div className="updated">
          <Icon name="refresh" size={14} /> Updated 30 sec ago{" "}
          <Button>Refresh</Button>
        </div>
      </div>
      <div className="kpi-grid">
        {metrics.map(([label, value, delta, sub, tone]) => (
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
            meta="Operational exceptions that may need dispatcher intervention"
            action={
              <button className="text-button" onClick={goExceptions}>
                View all exceptions <Icon name="arrow" size={14} />
              </button>
            }
          />
          <div className="attention-list">
            {exceptions.slice(0, 4).map((x, i) => (
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
            ))}
          </div>
        </section>
        <section className="panel status-panel">
          <SectionTitle title="Order status" meta="2,431 total orders" />
          <div className="donut-wrap">
            <div className="donut">
              <div>
                <strong>2,431</strong>
                <span>Total orders</span>
              </div>
            </div>
            <div className="legend">
              {[
                ["Delivered", "1,647", "67.7%"],
                ["In delivery", "612", "25.2%"],
                ["Processing", "115", "4.7%"],
                ["Exception", "57", "2.3%"],
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

function DriversPage() {
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Drivers</h1>
          <p>Monitor driver availability, workload, and performance</p>
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
        <FilterBar search="Search driver name or ID" />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Driver</th>
                <th>Status</th>
                <th>Current order</th>
                <th>Workload</th>
                <th>Vehicle</th>
                <th>Last activity</th>
                <th>Completed today</th>
                <th>Failed today</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {drivers.map((d) => (
                <tr key={d.id}>
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
                        <small>{d.id}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <Badge>{d.status}</Badge>
                  </td>
                  <td>
                    <span className={d.order !== "—" ? "link" : ""}>
                      {d.order}
                    </span>
                  </td>
                  <td>{d.workload}</td>
                  <td>{d.vehicle}</td>
                  <td>{d.activity}</td>
                  <td>{d.completed}</td>
                  <td>{d.failed}</td>
                  <td>
                    <Icon name="dots" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination label="Showing 1–7 of 493" />
      </section>
    </div>
  )
}

function ExceptionsPage({ openOrder }: { openOrder: (id: string) => void }) {
  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Exceptions & alerts</h1>
          <p>
            Investigate and resolve operational issues requiring human attention
          </p>
        </div>
        <Button icon="refresh">Refresh</Button>
      </div>
      <div className="exception-banner">
        <div>
          <Icon name="alert" />
          <div>
            <strong>5 critical exceptions require immediate attention</strong>
            <span>Oldest exception has been open for 24 minutes</span>
          </div>
        </div>
        <Button variant="danger">Review critical</Button>
      </div>
      <div className="subtabs">
        <button className="active">
          Open <span>27</span>
        </button>
        <button>
          Assigned to me <span>4</span>
        </button>
        <button>
          Monitoring <span>8</span>
        </button>
        <button>
          Resolved today <span>43</span>
        </button>
      </div>
      <section className="panel table-panel">
        <FilterBar search="Search exception or order ID" />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Exception ID</th>
                <th>Related order</th>
                <th>Type</th>
                <th>Severity</th>
                <th>Created</th>
                <th>Age</th>
                <th>Current state</th>
                <th>Assigned operator</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {exceptions.map((x) => (
                <tr key={x.id}>
                  <td>
                    <strong>{x.id}</strong>
                  </td>
                  <td>
                    <span className="link" onClick={() => openOrder(x.order)}>
                      {x.order}
                    </span>
                  </td>
                  <td>{x.type}</td>
                  <td>
                    <Badge>{x.severity}</Badge>
                  </td>
                  <td>
                    {x.created}
                    <small>Today</small>
                  </td>
                  <td
                    className={x.severity === "Critical" ? "danger-text" : ""}
                  >
                    {x.age}
                  </td>
                  <td>
                    <Badge>{x.state}</Badge>
                  </td>
                  <td>{x.operator}</td>
                  <td>
                    <Button
                      variant={
                        x.severity === "Critical" ? "primary" : "secondary"
                      }
                    >
                      Investigate
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination label="Showing 1–6 of 27" />
      </section>
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
}: {
  id: string
  back: () => void
  assign: () => void
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
        <div>
          <Button>Cancel order</Button>
          {failed ? (
            <Button variant="primary" icon="user" onClick={assign}>
              Assign driver
            </Button>
          ) : (
            <Button icon="route">Reassign driver</Button>
          )}
        </div>
      </div>
      {failed && (
        <div className="failure-banner">
          <div>
            <Icon name="alert" />
            <div>
              <strong>Automatic assignment failed after 3 attempts</strong>
              <span>
                No candidate driver accepted within the configured response
                window. Dispatcher intervention is recommended.
              </span>
            </div>
          </div>
          <Button variant="danger" onClick={assign}>
            Assign driver
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

function AssignModal({ close }: { close: () => void }) {
  const [selected, setSelected] = useState("DRV-0881")
  return (
    <div className="modal-backdrop">
      <div className="modal">
        <div className="modal-head">
          <div>
            <h2>Assign driver manually</h2>
            <p>Order SXP-240519-8842 · Pickup at Westgate Mall</p>
          </div>
          <button className="icon-button" onClick={close}>
            <Icon name="close" />
          </button>
        </div>
        <div className="modal-notice">
          <Icon name="alert" size={17} />
          <span>
            Manual assignment overrides the automatic assignment workflow. The
            selected driver will receive an immediate request.
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
            <Button variant="primary" onClick={close}>
              Assign selected driver
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function App() {
  const [page, setPage] = useState<Page>("Dashboard")
  const [order, setOrder] = useState<string | null>(null)
  const [modal, setModal] = useState(false)
  const openOrder = (id: string) => setOrder(id)
  let content: ReactNode
  if (order)
    content = (
      <OrderDetail
        id={order}
        back={() => setOrder(null)}
        assign={() => setModal(true)}
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
      <AssignmentPage openModal={() => setModal(true)} openOrder={openOrder} />
    )
  else if (page === "Drivers") content = <DriversPage />
  else if (page === "Exceptions")
    content = <ExceptionsPage openOrder={openOrder} />
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
      {modal && <AssignModal close={() => setModal(false)} />}
    </AppShell>
  )
}
