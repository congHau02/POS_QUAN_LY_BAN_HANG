import { useState, lazy, Suspense } from "react";
import { formatCurrency } from "../../utils/formatCurrency";
import { CheckCircle2, Receipt } from "lucide-react";

const ReceiptPopup = lazy(() => import("../Explore/ReceiptPopup"));
const preloadReceiptPopup = () => import("../Explore/ReceiptPopup");

function CompletedPayment({ orderId, order }) {
  const [showReceipt, setShowReceipt] = useState(false);
  const currentOrder = order || { orderId };

  return (
    <>
      <div className="mt-4 p-5 border border-emerald-200 bg-emerald-50/50 rounded-2xl text-center shadow-xs">
        <h5 className="text-base font-bold text-emerald-800 mb-2">
          🎉 Thanh toán thành công!
        </h5>
        
        <div className="flex justify-center mb-3 text-emerald-600">
          <CheckCircle2 size={52} />
        </div>

        <div className="text-slate-800 text-sm space-y-1">
          <p>
            <strong>Mã đơn hàng:</strong> #{orderId || currentOrder.orderId}
          </p>
          {currentOrder.grandTotal !== undefined && (
            <p>
              <strong>Tổng thanh toán:</strong>{" "}
              <span className="text-emerald-700 font-bold">
                {formatCurrency(currentOrder.grandTotal)}
              </span>
            </p>
          )}
          {currentOrder.discountAmount > 0 && (
            <div className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 my-1">
              🏷️ Tiết kiệm: {formatCurrency(currentOrder.discountAmount)} (
              {currentOrder.promotionName || "Khuyến mãi"})
            </div>
          )}
          <p className="text-xs text-slate-500 pt-1">Cảm ơn bạn! Đơn hàng đã hoàn tất.</p>
        </div>

        {currentOrder && (
          <button
            type="button"
            className="inline-flex items-center gap-1.5 px-4 py-2 mt-4 rounded-lg border border-emerald-300 hover:bg-emerald-100/50 text-emerald-800 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
            onClick={() => setShowReceipt(true)}
            onMouseEnter={preloadReceiptPopup}
          >
            <Receipt size={15} />
            <span>Xem & In hóa đơn</span>
          </button>
        )}
      </div>

      {showReceipt && (
        <Suspense fallback={null}>
          <ReceiptPopup
            order={currentOrder}
            isOpen={showReceipt}
            onClose={() => setShowReceipt(false)}
          />
        </Suspense>
      )}
    </>
  );
}

export default CompletedPayment;
