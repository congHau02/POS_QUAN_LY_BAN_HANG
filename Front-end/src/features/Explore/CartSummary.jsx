import { useState, useEffect, useCallback, lazy, Suspense } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useCreateOrder } from "../Orders/useCreateOrder";
import PaymentQRCode from "../Payment/PaymentQRCode";
import toast from "react-hot-toast";

const ReceiptPopup = lazy(() => import("./ReceiptPopup"));
const preloadReceiptPopup = () => import("./ReceiptPopup");
import { formatCurrency } from "../../utils/formatCurrency";
import { evaluatePromotion } from "../../services/PromotionService";
import {
  usePromotionEvaluation,
  formatCartItems,
} from "../Promotions/usePromotionEvaluation";
import { useCartCalculations } from "./useCartCalculations";
import { getPromotionErrorMessage } from "../../utils/promotionErrorMessages";
import { buildCustomerOrderPayload } from "../../utils/customerSync";
import { getOrderStatus } from "../Payment/orderTerminalState";
import { Tag, Banknote, CreditCard, X } from "lucide-react";

function CartSummary({
  customer,
  onClearCustomer,
  cartItems,
  clearCart,
}) {
  const queryClient = useQueryClient();
  const { isCreating, createOrder, orderData } = useCreateOrder();

  const [showModal, setShowModal] = useState(false);
  const [completedCashOrder, setCompletedCashOrder] = useState(null);
  const [couponInput, setCouponInput] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState("");
  const [isApplyingCoupon, setIsApplyingCoupon] = useState(false);


  const {
    evaluation,
    isFetching: isEvaluatingPromo,
    error: promoError,
    cartSignature,
  } = usePromotionEvaluation({
    cartItems,
    couponCode: appliedCoupon,
  });

  const isEvaluating = isApplyingCoupon || isEvaluatingPromo;

  // Clear applied coupon if query evaluation produced an error
  useEffect(() => {
    if (promoError && appliedCoupon) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAppliedCoupon("");
    }
  }, [promoError, appliedCoupon]);

  const handleApplyCoupon = async () => {
    const code = couponInput.trim().toUpperCase();
    if (!code) {
      toast.error("Vui lòng nhập mã giảm giá");
      return;
    }
    if (!cartItems.length) {
      toast.error("Giỏ hàng đang trống");
      return;
    }

    const formattedCartItems = formatCartItems(cartItems);

    setIsApplyingCoupon(true);
    try {
      const res = await evaluatePromotion({
        couponCode: code,
        cartItems: formattedCartItems,
      });
      queryClient.setQueryData(
        ["promotion-evaluation", cartSignature, code],
        res,
      );
      setAppliedCoupon(code);
      toast.success("Áp dụng mã giảm giá thành công");
    } catch (err) {
      toast.error(getPromotionErrorMessage(err));
    } finally {
      setIsApplyingCoupon(false);
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon("");
    setCouponInput("");
    toast.success("Đã gỡ mã giảm giá");
  };

  const {
    subtotal,
    discountAmount,
    tax,
    grandTotal,
  } = useCartCalculations(cartItems, evaluation);

  const activeEvaluation = evaluation;

  function onCreateOrder(paymentMode) {
    const formattedCartItems = formatCartItems(cartItems);
    const customerPayload = buildCustomerOrderPayload(customer);

    const dataForm = {
      ...customerPayload,
      cartItems: formattedCartItems,
      subtotal,
      discountAmount,
      tax,
      grandTotal,
      appliedPromotionId: activeEvaluation?.appliedPromotionId || null,
      couponCode: appliedCoupon || null,
      paymentMethod: paymentMode.toUpperCase(),
    };

    createOrder(dataForm, {
      onSuccess: (dt) => {
        queryClient.invalidateQueries({ queryKey: ["items"] });
        queryClient.invalidateQueries({ queryKey: ["inventory-transactions"] });
        queryClient.invalidateQueries({ queryKey: ["customers"] });
        queryClient.invalidateQueries({ queryKey: ["promotions"] });
        queryClient.invalidateQueries({ queryKey: ["orders"] });

        if (paymentMode === "PAYOS") {
          setShowModal(true);
        } else if (
          paymentMode === "CASH" &&
          (dt?.paymentDetails?.status === "COMPLETED" || dt?.data?.paymentDetails?.status === "COMPLETED")
        ) {
          toast.success("Thanh toán thành công");
          setCompletedCashOrder(dt?.paymentDetails ? dt : (dt?.data || dt));
        }
      },
    });
  }

  const handleClearCart = useCallback(() => {
    if (onClearCustomer) {
      onClearCustomer();
    }
    setCouponInput("");
    setAppliedCoupon("");
    clearCart();
  }, [onClearCustomer, clearCart]);

  const handleClosePayOSModal = useCallback(() => {
    setShowModal(false);
    const activeId = orderData?.orderId || orderData?.data?.orderId;
    const cachedData = queryClient.getQueryData(["order", activeId]);
    const status = getOrderStatus(cachedData) || getOrderStatus(orderData);
    if (status === "COMPLETED") {
      handleClearCart();
    }
  }, [orderData, queryClient, handleClearCart]);

  return (
    <div className="space-y-3">
      {/* Coupon Input Section */}
      <div className="flex gap-1.5">
        <input
          type="text"
          placeholder="Mã giảm giá"
          value={couponInput}
          onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
          disabled={isEvaluating || Boolean(appliedCoupon)}
          className="flex-1 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs uppercase font-medium text-slate-900 placeholder-slate-400 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-slate-50 transition-colors"
        />
        {appliedCoupon ? (
          <button
            type="button"
            onClick={handleRemoveCoupon}
            disabled={isEvaluating}
            className="px-2.5 py-1.5 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-xs font-semibold transition-colors cursor-pointer"
          >
            Gỡ mã
          </button>
        ) : (
          <button
            type="button"
            onClick={handleApplyCoupon}
            disabled={isEvaluating || !cartItems.length}
            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition-colors disabled:opacity-40 cursor-pointer"
          >
            <Tag size={12} />
            <span>{isEvaluating ? "..." : "Áp dụng"}</span>
          </button>
        )}
      </div>

      {/* Active Promotion Badges */}
      {activeEvaluation?.appliedPromotionType === "HAPPY_HOUR" && (
        <div className="p-2 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-center justify-between">
          <span>
            ⚡ Khung giờ vàng: <strong>{activeEvaluation.appliedPromotionName}</strong>
          </span>
        </div>
      )}

      {activeEvaluation?.appliedPromotionType === "COUPON" && (
        <div className="p-2 rounded-lg bg-emerald-50 border border-emerald-200 text-xs text-emerald-800 flex items-center justify-between">
          <span>
            🏷️ Mã ưu đãi: <strong>{appliedCoupon}</strong> ({activeEvaluation.appliedPromotionName})
          </span>
        </div>
      )}

      {activeEvaluation?.appliedPromotionType === "BOGO" && (
        <div className="p-2 rounded-lg bg-blue-50 border border-blue-200 text-xs text-blue-800 flex items-center justify-between">
          <span>
            🎁 Mua 1 Tặng 1: <strong>{activeEvaluation.appliedPromotionName}</strong>
          </span>
        </div>
      )}

      {/* Pricing Breakdown */}
      <div className="space-y-1.5 pt-1 text-xs">
        <div className="flex justify-between text-slate-500">
          <span>Tạm tính:</span>
          <span className="text-slate-800 font-medium">{formatCurrency(subtotal)}</span>
        </div>

        {discountAmount > 0 && (
          <div className="flex justify-between text-red-600 font-semibold">
            <span>
              Giảm giá {activeEvaluation?.appliedPromotionName ? `(${activeEvaluation.appliedPromotionName})` : ""}:
            </span>
            <span>-{formatCurrency(discountAmount)}</span>
          </div>
        )}

        <div className="flex justify-between text-slate-500">
          <span>Thuế VAT (10%):</span>
          <span className="text-slate-800 font-medium">{formatCurrency(tax)}</span>
        </div>

        <div className="border-t border-slate-200 pt-2 flex justify-between items-center text-sm font-bold text-slate-900">
          <span>Tổng thanh toán:</span>
          <span className="text-base text-blue-600">
            {formatCurrency(grandTotal)}
          </span>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="grid grid-cols-2 gap-2 pt-1">
        <button
          type="button"
          onClick={() => onCreateOrder("CASH")}
          onMouseEnter={preloadReceiptPopup}
          disabled={isCreating || !cartItems.length}
          className="inline-flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-xs transition-colors disabled:opacity-40 cursor-pointer"
        >
          <Banknote size={15} />
          <span>TIỀN MẶT</span>
        </button>
        <button
          type="button"
          onClick={() => onCreateOrder("PAYOS")}
          disabled={isCreating || !cartItems.length}
          className="inline-flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-xs transition-colors disabled:opacity-40 cursor-pointer"
        >
          <CreditCard size={15} />
          <span>CHUYỂN KHOẢN</span>
        </button>
      </div>

      <div>
        <button
          type="button"
          onClick={() => handleClearCart()}
          className="w-full py-1.5 text-xs text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
        >
          Xóa giỏ hàng
        </button>
      </div>

      {/* Cash Order Receipt Modal */}
      {completedCashOrder && (
        <Suspense fallback={null}>
          <ReceiptPopup
            order={completedCashOrder}
            isOpen={Boolean(completedCashOrder)}
            onClose={() => {
              setCompletedCashOrder(null);
              handleClearCart();
            }}
          />
        </Suspense>
      )}

      {/* Modal PayOS */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs"
            onClick={handleClosePayOSModal}
          />

          <div className="relative w-full max-w-md bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden z-10 p-5">
            <button
              type="button"
              className="absolute top-4 right-4 p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              onClick={handleClosePayOSModal}
              aria-label="Đóng"
            >
              <X size={18} />
            </button>

            <div>
              {orderData && (
                <PaymentQRCode
                  orderData={orderData}
                  onCancelSuccess={() => {
                    setShowModal(false);
                    handleClearCart();
                  }}
                  onSwitchToCashSuccess={(completedOrder) => {
                    setShowModal(false);
                    setCompletedCashOrder(completedOrder);
                    handleClearCart();
                  }}
                />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default CartSummary;
