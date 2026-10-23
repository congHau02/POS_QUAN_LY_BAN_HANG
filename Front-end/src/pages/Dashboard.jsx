import { useDashboard } from "../features/Dashboard/useDashboard";
import Spinner from "../ui/Spinner";
import { formatCurrency } from "../utils/formatCurrency";
import { formatLocalDateTime } from "../utils/activityLogDates";
import { Coins, ShoppingCart, History } from "lucide-react";

const PAYMENT_METHOD_LABELS = {
  CASH: "Tiền mặt",
  PAYOS: "PayOS",
  QR_CODE: "Mã QR",
};

const ORDER_STATUS_LABELS = {
  COMPLETED: "Hoàn thành",
  PENDING: "Chờ xử lý",
  CANCELLED: "Đã hủy",
};

function Dashboard() {
  const { isLoading, dashboardData } = useDashboard();

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-64">
        <Spinner size={32} className="text-blue-600" />
      </div>
    );
  }

  if (!dashboardData) {
    return (
      <div className="flex justify-center items-center h-64">
        <h2 className="text-lg font-medium text-slate-600">Không tìm thấy dữ liệu</h2>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm flex items-center gap-4 transition-all hover:shadow-md">
          <div className="w-14 h-14 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center flex-shrink-0">
            <Coins size={28} />
          </div>
          <div>
            <h3 className="text-sm font-medium text-slate-500">Doanh thu hôm nay</h3>
            <p className="text-2xl font-bold text-slate-900 mt-1">
              {formatCurrency(dashboardData.todaySales || 0)}
            </p>
          </div>
        </div>

        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm flex items-center gap-4 transition-all hover:shadow-md">
          <div className="w-14 h-14 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center flex-shrink-0">
            <ShoppingCart size={28} />
          </div>
          <div>
            <h3 className="text-sm font-medium text-slate-500">Đơn hàng hôm nay</h3>
            <p className="text-2xl font-bold text-slate-900 mt-1">
              {dashboardData.todayOrderCount ?? 0}
            </p>
          </div>
        </div>
      </div>

      {/* Recent Orders Card */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center gap-2">
          <History size={20} className="text-slate-500" />
          <h3 className="text-lg font-semibold text-slate-900">Đơn hàng gần đây</h3>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50 sticky top-0">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Mã đơn hàng
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Khách hàng
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Số tiền
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Thanh toán
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Trạng thái
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Ngày tạo
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {dashboardData.recentOrders?.map((order) => {
                const status = order.paymentDetails?.status || "PENDING";
                const isCompleted = status === "COMPLETED";
                const isPending = status === "PENDING";

                return (
                  <tr key={order.orderId} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900">
                      {order.orderId
                        ? order.orderId.length > 8
                          ? `${order.orderId.substring(0, 8)}...`
                          : order.orderId
                        : "—"}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-600">
                      {order.customerName}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-slate-900">
                      {formatCurrency(order.grandTotal)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-600">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800">
                        {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod || "Chưa xác định"}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          isCompleted
                            ? "bg-emerald-100 text-emerald-800"
                            : isPending
                            ? "bg-amber-100 text-amber-800"
                            : "bg-red-100 text-red-800"
                        }`}
                      >
                        {ORDER_STATUS_LABELS[status] || status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500">
                      {order.createdAt ? formatLocalDateTime(order.createdAt) : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default Dashboard;
