import { useState, useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { usePolledOrderData } from "./usePolledOrderData";
import { useOrderLifecycle } from "../Orders/useOrderLifecycle";
import CompletedPayment from "./CompletedPayment";
import ConfirmDeleteModal from "../../ui/ConfirmDeleteModal";
import QRCode from "./QRCode";
import { getOrderStatus } from "./orderTerminalState";

function PaymentQRCode({ orderData, onCancelSuccess, onSwitchToCashSuccess }) {
  const queryClient = useQueryClient();
  const rawOrder = orderData?.data || orderData;
  const orderId = rawOrder?.orderId;
  const [isConfirmCancelOpen, setIsConfirmCancelOpen] = useState(false);

  // 1. Đăng ký nhận dữ liệu phản ứng qua React Query (loại bỏ getQueryData tĩnh trong render)
  const { polledOrderData } = usePolledOrderData(rawOrder, orderId);

  // 2. Hợp nhất dữ liệu mới nhất để render giao diện
  const currentData = polledOrderData?.data || polledOrderData || rawOrder;
  const orderStatus = getOrderStatus(currentData);
  const isFinalCompleted = orderStatus === "COMPLETED";
  const isFinalCancelled = orderStatus === "CANCELLED";

  // 3. Sử dụng custom hook tập trung cho vòng đời đơn hàng
  const {
    cancelOrder,
    isCancelling,
    switchToCash,
    isSwitchingToCash,
  } = useOrderLifecycle({
    onCancelSuccess: () => {
      setIsConfirmCancelOpen(false);
      onCancelSuccess?.();
    },
    onSwitchToCashSuccess: (data) => {
      onSwitchToCashSuccess?.(data);
    },
  });

  // 4. Chỉ dùng useEffect cho Side-effect (Invalidate query bên ngoài), tuyệt đối không setState
  useEffect(() => {
    if (isFinalCompleted || isFinalCancelled) {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
    }
  }, [isFinalCompleted, isFinalCancelled, queryClient]);

  // Bảo vệ an toàn
  if (!currentData || currentData.paymentMethod !== "PAYOS") {
    return null;
  }

  if (isFinalCompleted) {
    return <CompletedPayment orderId={currentData.orderId} order={currentData} />;
  }

  return (
    <>
      <QRCode
        currentData={currentData}
        onCancelOrder={() => setIsConfirmCancelOpen(true)}
        onSwitchToCash={() => switchToCash(orderId)}
        isCancelling={isCancelling}
        isSwitchingToCash={isSwitchingToCash}
      />

      {isConfirmCancelOpen && (
        <ConfirmDeleteModal
          isOpen={isConfirmCancelOpen}
          onClose={() => setIsConfirmCancelOpen(false)}
          onConfirm={() => cancelOrder(orderId)}
          isLoading={isCancelling}
          title="Xác nhận hủy đơn hàng"
          entityName={`Đơn hàng #${orderId}`}
          message="Bạn có chắc chắn muốn hủy đơn hàng này không? Tồn kho của các sản phẩm sẽ được tự động hoàn lại vào kho."
          confirmText="Hủy đơn hàng"
          cancelText="Quay lại"
        />
      )}
    </>
  );
}

export default PaymentQRCode;
