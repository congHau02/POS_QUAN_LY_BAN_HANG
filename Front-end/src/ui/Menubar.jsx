import { useState, useEffect } from "react";
import { NavLink, useLocation } from "react-router-dom";
import useLogout from "../features/Auth/useLogout";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { useOutsideClick } from "../hooks/useOutsideClick";
import { 
  LayoutDashboard, Compass, Package, Tags, SlidersHorizontal, Users, 
  Percent, UserCircle, History, LogOut, Settings, Activity, Menu, X, ChevronDown
} from "lucide-react";

const ADMIN_LINKS = [
  { to: "/items", icon: Package, label: "Mặt hàng" },
  { to: "/categories", icon: Tags, label: "Danh mục" },
  { to: "/modifiers", icon: SlidersHorizontal, label: "Tùy chọn" },
  { to: "/users", icon: Users, label: "Người dùng" },
  { to: "/promotions", icon: Percent, label: "Khuyến mãi" },
  { to: "/customers", icon: UserCircle, label: "Khách hàng" },
];

const navLinkClass = ({ isActive }) =>
  `flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
    isActive
      ? "bg-blue-50 text-blue-700"
      : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
  }`;

function Menubar() {
  const { logout } = useLogout();
  const { user, isAdmin } = useCurrentUser();
  const location = useLocation();

  const [isManageOpen, setIsManageOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const manageRef = useOutsideClick(() => setIsManageOpen(false));
  const profileRef = useOutsideClick(() => setIsProfileOpen(false));
  const navRef = useOutsideClick(() => setIsMobileMenuOpen(false));

  // Auto-close dropdowns and mobile menu on route changes
  useEffect(() => {
    setIsManageOpen(false);
    setIsProfileOpen(false);
    setIsMobileMenuOpen(false);
  }, [location.pathname]);

  return (
    <nav ref={navRef} className="bg-white/90 backdrop-blur-md border-b border-slate-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex">
            <NavLink to="/dashboard" className="shrink-0 flex items-center gap-2">
              <div className="w-8 h-8 bg-blue-600 text-white rounded-lg flex items-center justify-center font-bold text-xl shadow-sm">
                B
              </div>
              <span className="font-bold text-xl text-slate-900 tracking-tight hidden md:block">BillingApp</span>
            </NavLink>
            
            {/* Desktop Navigation */}
            <div className="hidden md:ml-8 md:flex md:space-x-2 md:items-center">
              <NavLink className={navLinkClass} to="/dashboard">
                <LayoutDashboard size={18} />
                <span>Tổng quan</span>
              </NavLink>
              <NavLink className={navLinkClass} to="/explore">
                <Compass size={18} />
                <span>Bán hàng</span>
              </NavLink>
              
              <NavLink className={navLinkClass} to="/orders">
                <History size={18} />
                <span>Đơn hàng</span>
              </NavLink>

              {isAdmin && (
                <div ref={manageRef} className="relative flex items-center">
                  <button 
                    type="button"
                    onClick={() => setIsManageOpen((prev) => !prev)}
                    className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                      isManageOpen 
                        ? "bg-slate-100 text-slate-900" 
                        : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                    }`}
                  >
                    <Settings size={18} />
                    <span>Quản lý</span>
                    <ChevronDown size={14} className={`transition-transform duration-200 ${isManageOpen ? "rotate-180" : ""}`} />
                  </button>

                  {isManageOpen && (
                    <div className="absolute top-full left-0 mt-1 w-48 bg-white border border-slate-200 rounded-lg shadow-lg py-1 z-50">
                      {ADMIN_LINKS.map((link) => (
                        <NavLink
                          key={link.to}
                          to={link.to}
                          onClick={() => setIsManageOpen(false)}
                          className={({ isActive }) =>
                            `flex items-center gap-2 px-4 py-2 text-sm transition-colors ${
                              isActive ? "bg-blue-50 text-blue-700 font-medium" : "text-slate-700 hover:bg-slate-50"
                            }`
                          }
                        >
                          <link.icon size={16} />
                          {link.label}
                        </NavLink>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center">
            {/* Profile dropdown & User Identity */}
            <div ref={profileRef} className="relative ml-3 flex items-center gap-3">
              {/* User Display & Role Badge */}
              <div className="hidden sm:flex flex-col items-end text-right">
                <span className="text-sm font-semibold text-slate-900 leading-tight">
                  {user?.name || user?.email || "Người dùng"}
                </span>
                <span
                  className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wide mt-0.5 border ${
                    isAdmin
                      ? "bg-purple-50 text-purple-700 border-purple-200"
                      : "bg-blue-50 text-blue-700 border-blue-200"
                  }`}
                >
                  {isAdmin ? "Quản trị viên" : "Nhân viên"}
                </span>
              </div>

              <div>
                <button
                  type="button"
                  className="flex items-center gap-1.5 rounded-full bg-slate-50 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 p-1 border border-slate-200 transition-shadow hover:bg-slate-100 cursor-pointer"
                  onClick={() => setIsProfileOpen((prev) => !prev)}
                >
                  <span className="sr-only">Mở menu người dùng</span>
                  <div
                    className={`h-8 w-8 rounded-full flex items-center justify-center font-bold text-xs ${
                      isAdmin
                        ? "bg-purple-100 text-purple-700 border border-purple-200"
                        : "bg-blue-100 text-blue-700 border border-blue-200"
                    }`}
                  >
                    {isAdmin ? "Admin" : "Staff"}
                  </div>
                  <ChevronDown
                    size={14}
                    className={`text-slate-400 mr-1 transition-transform duration-200 ${
                      isProfileOpen ? "rotate-180" : ""
                    }`}
                  />
                </button>
              </div>

              {isProfileOpen && (
                <div className="absolute right-0 top-full z-50 mt-2 w-56 origin-top-right rounded-lg bg-white py-1 shadow-lg border border-slate-200 focus:outline-none">
                  <div className="px-4 py-2.5 border-b border-slate-100">
                    <p className="text-sm font-semibold text-slate-900 truncate">
                      {user?.name || (isAdmin ? "Quản trị viên" : "Nhân viên")}
                    </p>
                    <p className="text-xs text-slate-500 truncate">{user?.email}</p>
                    <div className="mt-1.5">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium border ${
                          isAdmin
                            ? "bg-purple-50 text-purple-700 border-purple-200"
                            : "bg-blue-50 text-blue-700 border-blue-200"
                        }`}
                      >
                        {isAdmin ? "Toàn quyền quản trị" : "Quyền truy cập tiêu chuẩn"}
                      </span>
                    </div>
                  </div>
                  <NavLink
                    to="/activity-logs"
                    onClick={() => setIsProfileOpen(false)}
                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    <Activity size={16} /> Nhật ký hoạt động
                  </NavLink>
                  <button
                    type="button"
                    onClick={() => setIsProfileOpen(false)}
                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    <Settings size={16} /> Cài đặt
                  </button>
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      logout();
                    }}
                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50 border-t border-slate-100 transition-colors"
                  >
                      <LogOut size={16} /> Đăng xuất
                  </button>
                </div>
              )}
            </div>
            
            {/* Mobile menu button */}
            <div className="flex items-center ml-4 md:hidden">
              <button
                type="button"
                className="inline-flex items-center justify-center rounded-md p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-500"
                onClick={() => setIsMobileMenuOpen((prev) => !prev)}
              >
                <span className="sr-only">Mở menu chính</span>
                {isMobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      {isMobileMenuOpen && (
        <div className="md:hidden border-t border-slate-200 bg-white">
          <div className="space-y-1 pb-3 pt-2 px-2">
            <NavLink 
              className={navLinkClass} 
              to="/dashboard"
              onClick={() => setIsMobileMenuOpen(false)}
            >
              <LayoutDashboard size={18} /> Tổng quan
            </NavLink>
            <NavLink 
              className={navLinkClass} 
              to="/explore"
              onClick={() => setIsMobileMenuOpen(false)}
            >
              <Compass size={18} /> Bán hàng
            </NavLink>
            <NavLink 
              className={navLinkClass} 
              to="/orders"
              onClick={() => setIsMobileMenuOpen(false)}
            >
              <History size={18} /> Đơn hàng
            </NavLink>
            <NavLink 
              className={navLinkClass} 
              to="/activity-logs"
              onClick={() => setIsMobileMenuOpen(false)}
            >
              <Activity size={18} /> Nhật ký hoạt động
            </NavLink>
            
            {isAdmin && (
              <>
                <div className="px-3 pt-4 pb-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Quản lý
                </div>
                {ADMIN_LINKS.map((link) => (
                  <NavLink 
                    key={link.to} 
                    className={navLinkClass} 
                    to={link.to}
                    onClick={() => setIsMobileMenuOpen(false)}
                  >
                    <link.icon size={18} /> {link.label}
                  </NavLink>
                ))}
              </>
            )}
          </div>
        </div>
      )}
    </nav>
  );
}

export default Menubar;
