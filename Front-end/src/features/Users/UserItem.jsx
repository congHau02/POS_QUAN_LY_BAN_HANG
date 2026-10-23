import { memo } from "react";
import { Trash2, User, Edit3 } from "lucide-react";
import { isSelfAccount } from "./userUtils";

function UserItem({ user, onEdit, onDelete, currentUser }) {
  const isSelf = isSelfAccount(user, currentUser);

  return (
    <div className="p-3.5 rounded-xl border border-slate-200 bg-white shadow-xs flex items-center justify-between gap-3 hover:shadow-sm transition-all">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-600 flex items-center justify-center font-semibold text-sm">
          <User size={18} />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h4 className="text-sm font-semibold text-slate-900">{user.name}</h4>
            <span
              className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                user.role === "ROLE_ADMIN"
                  ? "bg-amber-100 text-amber-800"
                  : "bg-blue-50 text-blue-700"
              }`}
            >
              {user.role === "ROLE_ADMIN" ? "Admin" : "Staff"}
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">{user.email}</p>
        </div>
      </div>

      <div className="flex items-center gap-1">
        <button
          type="button"
          className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:text-blue-600 hover:bg-blue-50 transition-colors cursor-pointer"
          onClick={() => onEdit?.(user)}
          title="Chỉnh sửa người dùng"
        >
          <Edit3 size={16} />
        </button>

        <button
          type="button"
          disabled={isSelf}
          className={`p-1.5 rounded-lg border border-slate-200 transition-colors ${
            isSelf
              ? "text-slate-300 bg-slate-50 opacity-40 cursor-not-allowed"
              : "text-slate-500 hover:text-red-600 hover:bg-red-50 cursor-pointer"
          }`}
          onClick={() => {
            if (!isSelf) {
              onDelete?.(user);
            }
          }}
          title={isSelf ? "Không thể tự xóa tài khoản đang đăng nhập" : "Xóa người dùng"}
          aria-label={isSelf ? "Không thể tự xóa tài khoản đang đăng nhập" : "Xóa người dùng"}
        >
          <Trash2 size={16} />
        </button>
      </div>
    </div>
  );
}

export default memo(UserItem);
