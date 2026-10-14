import { useState, useDeferredValue, lazy, Suspense } from "react";
import { useDebounce } from "../hooks/useDebounce";
import { useOrders } from "../features/Orders/useOrders";
import { useOrderLifecycle } from "../features/Orders/useOrderLifecycle";
import Spinner from "../ui/Spinner";
import ConfirmDeleteModal from "../ui/ConfirmDeleteModal";
import { formatCurrency } from "../utils/formatCurrency";

const ReceiptPopup = lazy(() => import("../features/Explore/ReceiptPopup"));
const preloadReceiptPopup = () => import("../features/Explore/ReceiptPopup");
import {
  Receipt,
  PackageOpen,
  Search,
  X,
  ChevronLeft,
  ChevronRight,
  Ban,
  Banknote,
} from "lucide-react";

const ORDER_STATUS_LABELS = {
  COMPLETED: "Hoàn thành",
  PENDING: "Chờ xử lý",
  CANCELLED: "Đã hủy",
};

const PAYMENT_METHOD_LABELS = {
  CASH: "Tiền mặt",
  PAYOS: "PayOS",
  QR_CODE: "Mã QR",
};

function OrderHistory() {
  // Filter & Pagination States
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearch = useDebounce(searchTerm, 300);
  const deferredSearch = useDeferredValue(debouncedSearch);
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selectedOrderForReceipt, setSelectedOrderForReceipt] = useState(null);
  const [orderToCancel, setOrderToCancel] = useState(null);
  const [orderToSwitchToCash, setOrderToSwitchToCash] = useState(null);

  const {
    cancelOrder,
    isCancelling,
    switchToCash,
    isSwitchingToCash,
  } = useOrderLifecycle({
    onCancelSuccess: () => {
      setOrderToCancel(null);
    },
    onSwitchToCashSuccess: (updatedOrder) => {
      setOrderToSwitchToCash(null);
      if (updatedOrder) {
        setSelectedOrderForReceipt(updatedOrder);
      }
    },
  });

  // Query paginated orders
  const result = useOrders({
    page,
    size: pageSize,
    search: deferredSearch,
    status: statusFilter,
  });

  const isLoading = result?.isLoading;
  const isFetching = result?.isFetching;
  const rawOrders = result?.orders ?? result?.data?.content ?? (Array.isArray(result?.data) ? result.data : []);
  const orders = Array.isArray(rawOrders) ? rawOrders : [];
  const totalElements = result?.totalElements ?? result?.data?.totalElements ?? orders.length;
  const totalPages = result?.totalPages ?? result?.data?.totalPages ?? Math.max(1, Math.ceil(totalElements / pageSize));

  // Handle Search Input Change
  const handleSearchChange = (e) => {
    setSearchTerm(e.target.value);
    setPage(0);
  };

  // Handle Clear Search
  const handleClearSearch = () => {
    setSearchTerm("");
    setPage(0);
  };

  // Handle Status Dropdown Change
  const handleStatusChange = (e) => {
    setStatusFilter(e.target.value);
    setPage(0);
  };

  // Handle Reset All Filters
  const handleResetFilters = () => {
    setSearchTerm("");
    setStatusFilter("");
    setPage(0);
  };

  // Format Items
  const formatItems = (items) => {
    return items?.map((item) => `${item.name} x ${item.quantity}`).join(", ") || "";
  };

  // Format Date
  const formatDate = (dateString) => {
    if (!dateString) return "";
    const options = {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    };
    return new Date(dateString).toLocaleDateString("vi-VN", options);
  };

  // Dynamic Page Window Calculation
  const getPageNumbers = (current, total) => {
    if (total <= 5) {
      return Array.from({ length: total }, (_, i) => i);
    }
    if (current <= 2) {
      return [0, 1, 2, "...", total - 1];
    }
    if (current >= total - 3) {
      return [0, "...", total - 3, total - 2, total - 1];
    }
    return [0, "...", current - 1, current, current + 1, "...", total - 1];
  };

  // Initial Loading Spinner (Only strictly on cold initial mount before any data exists)
  const isInitialLoading = isLoading && !result?.data;
  if (isInitialLoading) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center">
        <Spinner size={36} className="text-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full h-[calc(100vh-4rem)] flex flex-col overflow-hidden">
      {/* Header & Stats Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 shrink-0">
        <div className="flex items-center gap-3">
          <h2 className="text-2xl font-bold text-slate-900">Danh sách đơn hàng</h2>
          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
            {totalElements} đơn hàng
          </span>
          {isFetching && !isLoading && (
            <span className="text-xs text-slate-400 flex items-center gap-1.5 ml-2">
              <Spinner size={14} className="text-blue-600" />
              <span>Đang tải...</span>
            </span>
          )}
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-white p-3 rounded-xl border border-slate-200 shadow-xs mb-4 shrink-0 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="flex flex-1 flex-col sm:flex-row items-stretch sm:items-center gap-3">
          {/* Search Input Container */}
          <div className="relative flex-1 max-w-md">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              type="text"
              value={searchTerm}
              onChange={handleSearchChange}
              placeholder="Tìm theo mã ĐH, tên khách, SĐT..."
              className="w-full pl-9 pr-9 py-2 text-sm bg-slate-50/50 border border-slate-200 rounded-lg text-slate-900 placeholder:text-slate-400 focus:outline-hidden focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white transition-colors shadow-2xs"
            />
            {searchTerm && (
              <button
                type="button"
                onClick={handleClearSearch}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5 rounded-md transition-colors cursor-pointer"
                title="Xóa tìm kiếm"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Payment Status Filter Dropdown */}
          <div className="w-full sm:w-56 shrink-0 relative">
            <select
              value={statusFilter}
              onChange={handleStatusChange}
              className="w-full px-3 py-2 text-sm bg-slate-50/50 border border-slate-200 rounded-lg text-slate-700 focus:outline-hidden focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:bg-white transition-colors shadow-2xs cursor-pointer"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="PENDING">Chờ xử lý</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </div>
        </div>

        {/* Clear Filter Button */}
        {(searchTerm || statusFilter) && (
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-xs text-blue-600 hover:text-blue-800 font-medium px-2 py-1 rounded-md hover:bg-blue-50 transition-colors self-end sm:self-center cursor-pointer"
          >
            Đặt lại bộ lọc
          </button>
        )}
      </div>

      {/* Main Table Card (Flex-1, min-h-0, Internal Scroll) */}
      <div className="flex-1 min-h-0 bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col overflow-hidden">
        {/* Scrollable Table Area */}
        <div className="flex-1 min-h-0 overflow-y-auto overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left border-collapse">
            <thead className="sticky top-0 z-10 bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Mã ĐH</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Khách hàng</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Sản phẩm</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Khuyến mãi</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Tổng tiền</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Thanh toán</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Trạng thái</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Thời gian</th>
                <th className="px-5 py-3.5 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Thao tác</th>
              </tr>
            </thead>

            <tbody className={`divide-y divide-slate-100 bg-white transition-opacity duration-200 ${isFetching ? "opacity-60" : "opacity-100"}`}>
              {orders.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-16 text-center text-slate-500">
                    <PackageOpen size={44} className="mx-auto text-slate-400 mb-2" />
                    <p className="text-base font-medium text-slate-800">Không tìm thấy đơn hàng nào</p>
                    <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                      {searchTerm || statusFilter
                        ? "Không có đơn hàng nào khớp với từ khóa tìm kiếm hoặc bộ lọc trạng thái đã chọn."
                        : "Chưa có dữ liệu đơn hàng được ghi nhận trong hệ thống."}
                    </p>
                    {(searchTerm || statusFilter) && (
                      <button
                        type="button"
                        onClick={handleResetFilters}
                        className="mt-3 inline-flex items-center px-3 py-1.5 text-xs font-medium text-blue-600 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors cursor-pointer"
                      >
                        Xóa bộ lọc
                      </button>
                    )}
                  </td>
                </tr>
              ) : (
                orders.map((order) => {
                  const status = order.paymentDetails?.status || order.paymentStatus || "PENDING";
                  const isCompleted = status === "COMPLETED";
                  const isPending = status === "PENDING";

                  return (
                    <tr key={order.orderId} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-4 whitespace-nowrap text-sm font-semibold text-slate-900">
                        #{order.orderId}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-sm">
                        <div className="font-medium text-slate-900">{order.customerName}</div>
                        <div className="text-xs text-slate-500">{order.phoneNumber}</div>
                      </td>
                      <td className="px-5 py-4 text-sm text-slate-600 max-w-xs truncate" title={formatItems(order.items)}>
                        {formatItems(order.items)}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-sm">
                        {order.discountAmount > 0 || order.promotionName ? (
                          <div>
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-50 text-red-700 border border-red-200">
                              {order.promotionName || "Ưu đãi"}
                            </span>
                            {order.discountAmount > 0 && (
                              <div className="text-xs text-red-600 font-semibold mt-0.5">
                                -{formatCurrency(order.discountAmount)}
                              </div>
                            )}
                          </div>
                        ) : (
                          <span className="text-slate-400 text-xs">-</span>
                        )}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-sm font-bold text-slate-900">
                        {formatCurrency(order.grandTotal ?? order.totalAmount ?? 0)}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-sm">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                          {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod || "Chưa xác định"}
                        </span>
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-sm">
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
                      <td className="px-5 py-4 whitespace-nowrap text-xs text-slate-500">
                        {formatDate(order.createdAt || order.orderDate)}
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap text-center text-sm">
                        <div className="inline-flex items-center justify-center gap-1.5">
                          <button
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-medium transition-colors cursor-pointer"
                            title="Xem & In hóa đơn"
                            onClick={() => setSelectedOrderForReceipt(order)}
                            onMouseEnter={preloadReceiptPopup}
                          >
                            <Receipt size={14} className="text-blue-600" />
                            <span>In hóa đơn</span>
                          </button>
                          {isPending && (
                            <>
                              <button
                                type="button"
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-emerald-200 bg-emerald-50/50 hover:bg-emerald-100/70 text-emerald-700 text-xs font-medium transition-colors cursor-pointer"
                                title="Chuyển sang tiền mặt"
                                onClick={() => setOrderToSwitchToCash(order)}
                              >
                                <Banknote size={14} className="text-emerald-600" />
                                <span>Thu tiền mặt</span>
                              </button>
                              <button
                                type="button"
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-red-200 bg-red-50/50 hover:bg-red-100/70 text-red-600 text-xs font-medium transition-colors cursor-pointer"
                                title="Hủy đơn hàng"
                                onClick={() => setOrderToCancel(order)}
                              >
                                <Ban size={14} className="text-red-600" />
                                <span>Hủy đơn</span>
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Toolbar Bar (Fixed at bottom of Card) */}
        <div className="shrink-0 border-t border-slate-200 bg-slate-50/70 px-5 py-3 flex flex-col sm:flex-row items-center justify-between gap-3 text-sm">
          {/* Left: Showing X to Y of Z orders */}
          <div className="text-xs sm:text-sm text-slate-600">
            Hiển thị{" "}
            <span className="font-semibold text-slate-900">
              {totalElements === 0 ? 0 : page * pageSize + 1}
            </span>{" "}
            đến{" "}
            <span className="font-semibold text-slate-900">
              {Math.min((page + 1) * pageSize, totalElements)}
            </span>{" "}
            trên tổng số{" "}
            <span className="font-semibold text-slate-900">{totalElements}</span>{" "}
            đơn hàng
          </div>

          {/* Right: Page Size Selector + Pagination Controls */}
          <div className="flex items-center gap-4">
            {/* Page size selector */}
            <div className="flex items-center gap-2 text-xs text-slate-600">
              <span className="hidden sm:inline">Số dòng mỗi trang:</span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
                className="px-2 py-1 text-xs bg-white border border-slate-200 rounded-md text-slate-700 focus:outline-hidden focus:ring-2 focus:ring-blue-500 shadow-2xs cursor-pointer"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>

            {/* Pagination buttons */}
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0 || isLoading}
                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-2xs cursor-pointer"
              >
                <ChevronLeft size={14} />
                <span className="hidden sm:inline">Trang trước</span>
              </button>

              {/* Page numbers */}
              <div className="flex items-center gap-1">
                {getPageNumbers(page, totalPages).map((p, idx) =>
                  p === "..." ? (
                    <span key={`ellipsis-${idx}`} className="px-2 text-xs text-slate-400">
                      ...
                    </span>
                  ) : (
                    <button
                      key={`page-${p}`}
                      type="button"
                      onClick={() => setPage(p)}
                      className={`min-w-[28px] h-7 px-2 flex items-center justify-center text-xs font-medium rounded-md transition-colors cursor-pointer ${
                        page === p
                          ? "bg-blue-600 text-white font-semibold shadow-2xs"
                          : "bg-white border border-slate-200 text-slate-700 hover:bg-slate-50"
                      }`}
                    >
                      {p + 1}
                    </button>
                  )
                )}
              </div>

              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1 || isLoading}
                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-2xs cursor-pointer"
              >
                <span className="hidden sm:inline">Trang sau</span>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Receipt Modal */}
      {selectedOrderForReceipt && (
        <Suspense fallback={null}>
          <ReceiptPopup
            order={selectedOrderForReceipt}
            isOpen={Boolean(selectedOrderForReceipt)}
            onClose={() => setSelectedOrderForReceipt(null)}
          />
        </Suspense>
      )}

      {/* Switch to Cash Confirmation Modal */}
      {orderToSwitchToCash && (
        <ConfirmDeleteModal
          isOpen={Boolean(orderToSwitchToCash)}
          onClose={() => setOrderToSwitchToCash(null)}
          onConfirm={() => switchToCash(orderToSwitchToCash.orderId)}
          isLoading={isSwitchingToCash}
          title="Xác nhận thanh toán tiền mặt"
          entityName={`Đơn hàng #${orderToSwitchToCash.orderId}`}
          message="Khách hàng muốn chuyển sang thanh toán bằng Tiền mặt? Hệ thống sẽ hoàn tất đơn hàng và mở hóa đơn để in."
          confirmText="Thu tiền mặt"
          cancelText="Quay lại"
        />
      )}

      {/* Cancel Confirmation Modal */}
      {orderToCancel && (
        <ConfirmDeleteModal
          isOpen={Boolean(orderToCancel)}
          onClose={() => setOrderToCancel(null)}
          onConfirm={() => cancelOrder(orderToCancel.orderId)}
          isLoading={isCancelling}
          title="Xác nhận hủy đơn hàng"
          entityName={`Đơn hàng #${orderToCancel.orderId}`}
          message="Bạn có chắc chắn muốn hủy đơn hàng này không? Tồn kho của các sản phẩm sẽ được hoàn trả tự động vào kho."
          confirmText="Hủy đơn hàng"
          cancelText="Quay lại"
        />
      )}
    </div>
  );
}

export default OrderHistory;
