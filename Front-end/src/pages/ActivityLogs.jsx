import { useState, useDeferredValue } from "react";
import { useDebounce } from "../hooks/useDebounce";
import { useActivityLogs } from "../features/ActivityLogs/useActivityLogs";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { computeDatePresetRange, formatLocalDateTime } from "../utils/activityLogDates";
import Spinner from "../ui/Spinner";
import {
  Activity,
  History,
  Calendar,
  Filter,
  User,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";

function ActivityLogs() {
  const { isAdmin } = useCurrentUser();

  // Filter States
  const [datePreset, setDatePreset] = useState("all");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [userEmailFilter, setUserEmailFilter] = useState("");
  const debouncedUserEmail = useDebounce(userEmailFilter, 300);
  const deferredUserEmail = useDeferredValue(debouncedUserEmail);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  // Date Preset Handler
  const handlePresetChange = (preset) => {
    setDatePreset(preset);
    setPage(0);

    const { startDate: nextStartDate, endDate: nextEndDate } =
      computeDatePresetRange(preset);
    setStartDate(nextStartDate);
    setEndDate(nextEndDate);
  };

  const {
    isLoading,
    isFetching,
    logs,
    totalElements,
    totalPages,
  } = useActivityLogs({
    page,
    size: pageSize,
    userEmail: deferredUserEmail,
    action: actionFilter,
    startDate,
    endDate,
  });

  const formatDate = (dateString) => {
    return formatLocalDateTime(dateString, { second: "2-digit" });
  };

  const getActionBadge = (action) => {
    const act = (action || "").toUpperCase();
    switch (act) {
      case "CREATE":
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
            Tạo mới
          </span>
        );
      case "UPDATE":
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">
            Cập nhật
          </span>
        );
      case "DELETE":
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-800 border border-red-200">
            Xóa
          </span>
        );
      case "LOGIN":
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200">
            Đăng nhập
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
            {act}
          </span>
        );
    }
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

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full h-[calc(100vh-4rem)] flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 shrink-0">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
            <Activity size={22} />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-slate-900">Nhật ký hoạt động</h2>
            <p className="text-xs text-slate-500">Giám sát và kiểm toán toàn bộ hoạt động trong hệ thống</p>
          </div>
          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 ml-2">
            {totalElements} bản ghi
          </span>
          {isFetching && !isLoading && (
            <span className="text-xs text-slate-400 flex items-center gap-1.5 ml-2">
              <Spinner size={14} className="text-blue-600" />
              <span>Đang đồng bộ...</span>
            </span>
          )}
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-xs mb-4 shrink-0 flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* Preset Buttons */}
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-xs font-medium text-slate-500 flex items-center gap-1 mr-1">
              <Calendar size={14} /> Thời gian:
            </span>
            <button
              type="button"
              onClick={() => handlePresetChange("today")}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                datePreset === "today"
                  ? "bg-blue-600 text-white shadow-2xs"
                  : "bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200"
              }`}
            >
              Hôm nay
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange("7days")}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                datePreset === "7days"
                  ? "bg-blue-600 text-white shadow-2xs"
                  : "bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200"
              }`}
            >
              7 ngày qua
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange("30days")}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                datePreset === "30days"
                  ? "bg-blue-600 text-white shadow-2xs"
                  : "bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200"
              }`}
            >
              30 ngày qua
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange("all")}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                datePreset === "all"
                  ? "bg-blue-600 text-white shadow-2xs"
                  : "bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200"
              }`}
            >
              Tất cả
            </button>
          </div>

          {/* Action Filter & User Filter */}
          <div className="flex items-center gap-3 flex-wrap">
            {/* Action Filter */}
            <div className="flex items-center gap-1.5">
              <Filter size={14} className="text-slate-400" />
              <select
                value={actionFilter}
                onChange={(e) => {
                  setActionFilter(e.target.value);
                  setPage(0);
                }}
                className="px-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-700 focus:outline-hidden focus:ring-2 focus:ring-blue-500 cursor-pointer shadow-2xs"
              >
                <option value="">Tất cả hành động</option>
                <option value="LOGIN">Đăng nhập</option>
                <option value="CREATE">Tạo mới</option>
                <option value="UPDATE">Cập nhật</option>
                <option value="DELETE">Xóa</option>
              </select>
            </div>

            {/* Admin User Filter */}
            {isAdmin && (
              <div className="flex items-center gap-1.5">
                <User size={14} className="text-slate-400" />
                <input
                  type="text"
                  value={userEmailFilter}
                  onChange={(e) => {
                    setUserEmailFilter(e.target.value);
                    setPage(0);
                  }}
                  placeholder="Lọc theo email người dùng..."
                  className="px-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-700 placeholder:text-slate-400 focus:outline-hidden focus:ring-2 focus:ring-blue-500 shadow-2xs w-48"
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="flex-1 min-h-0 bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col overflow-hidden">
        <div className="flex-1 min-h-0 overflow-y-auto overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left border-collapse">
            <thead className="sticky top-0 z-10 bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Thời gian</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Người thực hiện</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Hành động</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Đối tượng</th>
                <th className="px-5 py-3.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Mô tả chi tiết</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100 bg-white">
              {isLoading && !logs.length ? (
                <tr>
                  <td colSpan={5} className="py-16 text-center">
                    <Spinner size={32} className="mx-auto text-blue-600" />
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-16 text-center text-slate-500">
                    <History size={40} className="mx-auto text-slate-400 mb-2" />
                    <p className="text-base font-medium text-slate-800">Không có bản ghi nhật ký nào</p>
                    <p className="text-xs text-slate-500 mt-1">
                      {actionFilter || userEmailFilter || datePreset !== "all"
                        ? "Không tìm thấy nhật ký phù hợp với bộ lọc hiện tại."
                        : "Chưa có hoạt động nào được ghi nhận."}
                    </p>
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.logId || log.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-500">
                      {formatDate(log.timestamp || log.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-sm font-medium text-slate-900">
                      {log.userEmail}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-sm">
                      {getActionBadge(log.action)}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-xs font-semibold text-slate-600">
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                        {log.entityType || "-"}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-700">
                      {log.description}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Toolbar */}
        <div className="shrink-0 border-t border-slate-200 bg-slate-50/70 px-5 py-3 flex flex-col sm:flex-row items-center justify-between gap-3 text-sm">
          <div className="text-xs sm:text-sm text-slate-600">
            Hiển thị{" "}
            <span className="font-semibold text-slate-900">
              {totalElements === 0 ? 0 : page * pageSize + 1}
            </span>{" "}
            đến{" "}
            <span className="font-semibold text-slate-900">
              {Math.min((page + 1) * pageSize, totalElements)}
            </span>{" "}
            trong tổng số{" "}
            <span className="font-semibold text-slate-900">{totalElements}</span>{" "}
            bản ghi
          </div>

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 text-xs text-slate-600">
              <span className="hidden sm:inline">Số dòng:</span>
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

            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0 || isLoading}
                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-2xs cursor-pointer"
              >
                <ChevronLeft size={14} />
                <span className="hidden sm:inline">Trước</span>
              </button>

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
                <span className="hidden sm:inline">Sau</span>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ActivityLogs;
