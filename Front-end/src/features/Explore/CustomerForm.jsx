import { useState, useEffect, useRef, useCallback, memo } from "react";
import { fetchCustomerByPhone, createCustomer } from "../../services/CustomerService";
import { formatCurrency } from "../../utils/formatCurrency";
import {
  normalizePhoneNumber,
  shouldTriggerCrmLookup,
  getNextCustomerOnPhoneChange,
  getNextCustomerOnNameChange,
} from "../../utils/customerSync";
import toast from "react-hot-toast";
import Spinner from "../../ui/Spinner";
import { Search, Star, UserPlus, X } from "lucide-react";

function CustomerForm({
  customer,
  onCustomerChange,
}) {
  const phone = customer?.mobileNumber ?? "";
  const name = customer?.customerName ?? "";

  const [isSearching, setIsSearching] = useState(false);
  const [customerInfo, setCustomerInfo] = useState(null);
  const [isNewCustomer, setIsNewCustomer] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const lastSearchedPhoneRef = useRef("");

  // Derive verified customer state aligned with current input
  const normalizedPhone = normalizePhoneNumber(phone);
  const effectiveCustomerInfo =
    customerInfo && customerInfo.phoneNumber === normalizedPhone
      ? customerInfo
      : null;
  const effectiveIsNewCustomer =
    normalizedPhone.length >= 8 && isNewCustomer && !effectiveCustomerInfo;

  const performCustomerLookup = useCallback(
    async (targetPhone) => {
      if (!targetPhone || targetPhone.length < 8) return;

      try {
        setIsSearching(true);
        lastSearchedPhoneRef.current = targetPhone;
        const data = await fetchCustomerByPhone(targetPhone);
        if (data) {
          setCustomerInfo(data);
          setIsNewCustomer(false);
          onCustomerChange?.({
            customerId: data.customerId,
            customerName: data.name,
            mobileNumber: data.phoneNumber,
          });
          toast.success(`Đã tìm thấy khách hàng: ${data.name}`);
        }
      } catch (err) {
        if (err.response?.status === 404) {
          setCustomerInfo(null);
          setIsNewCustomer(true);
        }
      } finally {
        setIsSearching(false);
      }
    },
    [onCustomerChange]
  );

  // Background debounced CRM lookup when at least 8 digits are entered
  useEffect(() => {
    const phoneToSearch = normalizePhoneNumber(customer?.mobileNumber || "");

    if (phoneToSearch.length < 8) {
      lastSearchedPhoneRef.current = "";
      return;
    }

    if (lastSearchedPhoneRef.current === phoneToSearch) {
      return;
    }

    if (!shouldTriggerCrmLookup(phoneToSearch, customerInfo?.phoneNumber)) {
      return;
    }

    let isCancelled = false;
    const timer = setTimeout(() => {
      if (!isCancelled) {
        performCustomerLookup(phoneToSearch);
      }
    }, 400);

    return () => {
      isCancelled = true;
      clearTimeout(timer);
    };
  }, [customer?.mobileNumber, customerInfo?.phoneNumber, performCustomerLookup]);

  const handlePhoneChange = (newPhone) => {
    setCustomerInfo(null);
    setIsNewCustomer(false);
    if (onCustomerChange) {
      onCustomerChange(getNextCustomerOnPhoneChange(customer || {}, newPhone));
    }
  };

  const handleNameChange = (newName) => {
    if (onCustomerChange) {
      onCustomerChange(getNextCustomerOnNameChange(customer || {}, newName));
    }
  };

  const handleResetCustomer = () => {
    setCustomerInfo(null);
    setIsNewCustomer(false);
    lastSearchedPhoneRef.current = "";
    if (onCustomerChange) {
      onCustomerChange({
        customerId: null,
        customerName: "",
        mobileNumber: "",
      });
    }
  };

  async function handleQuickSaveCustomer() {
    const currentName = (customer?.customerName || "").trim();
    const currentPhone = (customer?.mobileNumber || "").trim();

    if (!currentName) {
      toast.error("Vui lòng nhập tên khách hàng");
      return;
    }
    if (!currentPhone) {
      toast.error("Vui lòng nhập số điện thoại");
      return;
    }

    try {
      setIsSaving(true);
      const newCustomer = await createCustomer({
        name: currentName,
        phoneNumber: currentPhone,
      });
      setCustomerInfo(newCustomer);
      setIsNewCustomer(false);
      onCustomerChange?.({
        customerId: newCustomer.customerId,
        customerName: newCustomer.name,
        mobileNumber: newCustomer.phoneNumber,
      });
      toast.success("Đã lưu khách hàng mới vào CRM");
    } catch {
      toast.error("Không thể lưu khách hàng");
    } finally {
      setIsSaving(false);
    }
  }

  const hasCustomerData = Boolean(phone.trim() || name.trim());

  return (
    <div className="space-y-2">
      {/* Phone Number Input */}
      <div>
        <div className="flex gap-1.5">
          <input
            type="tel"
            id="mobileNumber"
            placeholder="Số điện thoại..."
            value={phone}
            onChange={(e) => handlePhoneChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                performCustomerLookup(normalizedPhone);
              }
            }}
            className="flex-1 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
          />
          <button
            type="button"
            onClick={() => performCustomerLookup(normalizedPhone)}
            disabled={isSearching || !normalizedPhone}
            title="Tìm khách hàng"
            className="p-1.5 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-600 disabled:opacity-40 cursor-pointer"
          >
            {isSearching ? <Spinner size={14} /> : <Search size={14} />}
          </button>
          {hasCustomerData && (
            <button
              type="button"
              onClick={handleResetCustomer}
              title="Xóa thông tin khách hàng"
              className="p-1.5 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Customer Name Input */}
      <div>
        <input
          type="text"
          id="customerName"
          placeholder="Tên khách hàng..."
          value={name}
          onChange={(e) => handleNameChange(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
        />
      </div>

      {effectiveCustomerInfo && (
        <div className="p-2 rounded-lg bg-emerald-50 border border-emerald-200 text-xs flex justify-between items-center">
          <div>
            <div className="inline-flex items-center gap-1 font-semibold text-emerald-900">
              <Star size={12} className="text-amber-500 fill-amber-500" />
              <span>Khách hàng cũ</span>
            </div>
            <div className="text-[11px] text-emerald-700">
              {effectiveCustomerInfo.orderCount} đơn hàng • {formatCurrency(effectiveCustomerInfo.totalSpent || 0)}
            </div>
          </div>
          <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-bold bg-emerald-200 text-emerald-900">
            Đã xác minh
          </span>
        </div>
      )}

      {effectiveIsNewCustomer && (
        <div className="p-2 rounded-lg bg-amber-50 border border-amber-200 text-xs flex justify-between items-center">
          <div className="text-amber-800 text-[11px]">
            Khách hàng mới chưa có trong CRM.
          </div>
          <button
            type="button"
            onClick={handleQuickSaveCustomer}
            disabled={isSaving || !name.trim()}
            className="inline-flex items-center gap-1 px-2 py-0.8 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-[11px] font-medium transition-colors disabled:opacity-50 cursor-pointer"
          >
            <UserPlus size={12} />
            <span>{isSaving ? "Đang lưu..." : "Lưu"}</span>
          </button>
        </div>
      )}
    </div>
  );
}

export default memo(CustomerForm);
