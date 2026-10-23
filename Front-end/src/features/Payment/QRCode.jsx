import { QRCodeSVG } from "qrcode.react";
import { formatCurrency } from "../../utils/formatCurrency";
import Spinner from "../../ui/Spinner";
import { ExternalLink, Banknote, Ban } from "lucide-react";
import { getOrderStatus } from "./orderTerminalState";

function QRCode({
  currentData,
  onCancelOrder,
  onSwitchToCash,
  isCancelling = false,
  isSwitchingToCash = false,
}) {
  const subtotal = currentData.subtotal;
  const discountAmount = currentData.discountAmount || 0;
  const tax = currentData.tax;
  const grandTotal = currentData.grandTotal || 0;
  const checkoutUrl =
    currentData.paymentDetails?.checkoutUrl || currentData.checkoutUrl;

  const status = getOrderStatus(currentData);
  const isCancelled = status === "CANCELLED";

  return (
    <div className="mt-3 p-5 border border-slate-200 bg-slate-50/70 rounded-2xl text-center shadow-xs">
      <h5
        className={`text-base font-bold mb-3 ${
          isCancelled ? "text-red-600" : "text-slate-900"
        }`}
      >
        {isCancelled ? "Đơn hàng đã bị hủy" : "Quét mã QR để thanh toán (PayOS)"}
      </h5>

      {isCancelled ? (
        <div className="my-4 p-5 rounded-xl border border-red-200 bg-red-50 text-center flex flex-col items-center justify-center space-y-2">
          <div className="w-12 h-12 rounded-full bg-red-100 flex items-center justify-center text-red-600">
            <Ban size={24} />
          </div>
          <h4 className="text-sm font-bold text-red-800">
            Đơn hàng này đã bị hủy
          </h4>
          <p className="text-xs text-red-600 max-w-xs">
            Đơn hàng đã bị hủy bỏ và tồn kho đã được hoàn lại. Không thể tiếp tục thanh toán cho đơn hàng này.
          </p>
        </div>
      ) : currentData.paymentDetails?.qrCode ? (
        <div className="flex justify-center mb-4">
          <div className="p-3 bg-white rounded-xl shadow-xs inline-block border border-slate-200">
            <QRCodeSVG
              value={currentData.paymentDetails.qrCode}
              size={200}
              bgColor={"#ffffff"}
              fgColor={"#000000"}
              level={"H"}
              includeMargin={false}
            />
          </div>
        </div>
      ) : (
        <div className="my-8 flex justify-center">
          <Spinner size={32} className="text-blue-600" />
        </div>
      )}

      <div className="text-slate-800 text-left bg-white p-4 rounded-xl border border-slate-200 max-w-sm mx-auto shadow-2xs space-y-1.5">
        <div className="flex justify-between text-xs">
          <span className="text-slate-500">Mã đơn hàng:</span>
          <span className="font-semibold text-slate-900">#{currentData.orderId}</span>
        </div>

        {subtotal !== undefined && subtotal !== null && (
          <div className="flex justify-between text-xs">
            <span className="text-slate-500">Tạm tính:</span>
            <span className="text-slate-900">{formatCurrency(subtotal)}</span>
          </div>
        )}

        {discountAmount > 0 && (
          <div className="flex justify-between text-xs text-red-600 font-semibold">
            <span>
              🏷️ Giảm giá {currentData.promotionName ? `(${currentData.promotionName})` : ""}:
            </span>
            <span>-{formatCurrency(discountAmount)}</span>
          </div>
        )}

        {tax !== undefined && tax !== null && (
          <div className="flex justify-between text-xs">
            <span className="text-slate-500">Thuế VAT (10%):</span>
            <span className="text-slate-900">{formatCurrency(tax)}</span>
          </div>
        )}

        <div className="border-t border-slate-200 pt-2 mt-2 flex justify-between items-center">
          <span className="text-sm font-bold text-slate-900">Tổng thanh toán:</span>
          <span className="text-base font-bold text-red-600">
            {formatCurrency(grandTotal)}
          </span>
        </div>

        {discountAmount > 0 && (
          <div className="mt-2 py-1 px-2 rounded-lg bg-emerald-50 text-emerald-700 text-center text-xs font-semibold border border-emerald-100">
            Đã áp dụng ưu đãi: Tiết kiệm {formatCurrency(discountAmount)}
          </div>
        )}

        <p className="text-[11px] text-slate-400 mt-2 text-center">
          {isCancelled
            ? "* Đơn hàng đã bị hủy. Mọi giao dịch phát sinh sẽ không được xử lý."
            : "* Đơn hàng sẽ tự động cập nhật trạng thái ngay sau khi bạn chuyển khoản thành công."}
        </p>
      </div>

      {/* In-Flight Action Controls */}
      {!isCancelled && (
        <div className="mt-4 max-w-sm mx-auto space-y-2">
          {onSwitchToCash && (
            <button
              type="button"
              onClick={onSwitchToCash}
              disabled={isSwitchingToCash || isCancelling}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
            >
              {isSwitchingToCash ? (
                <Spinner size={14} className="text-white" />
              ) : (
                <Banknote size={15} />
              )}
              <span>Chuyển sang tiền mặt</span>
            </button>
          )}

          {onCancelOrder && (
            <button
              type="button"
              onClick={onCancelOrder}
              disabled={isSwitchingToCash || isCancelling}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl border border-red-200 bg-red-50 hover:bg-red-100 text-red-600 text-xs font-semibold transition-colors disabled:opacity-50 cursor-pointer"
            >
              {isCancelling ? (
                <Spinner size={14} className="text-red-600" />
              ) : (
                <Ban size={14} />
              )}
              <span>Hủy đơn hàng</span>
            </button>
          )}
        </div>
      )}

      {!isCancelled && checkoutUrl && (
        <a
          href={checkoutUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1.5 px-4 py-2 mt-4 rounded-lg border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold transition-colors cursor-pointer"
        >
          <span>Hoặc mở trang thanh toán PayOS</span>
          <ExternalLink size={13} />
        </a>
      )}
    </div>
  );
}

export default QRCode;
