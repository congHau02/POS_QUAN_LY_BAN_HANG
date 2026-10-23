export const isTerminalOrderStatus = (status) =>
  status === "COMPLETED" || status === "CANCELLED";

export const getOrderStatus = (data) => {
  const current = data?.state?.data !== undefined ? data.state.data : data;
  const resolved = current?.data || current;
  return (
    resolved?.paymentDetails?.status ||
    resolved?.paymentStatus ||
    resolved?.status
  );
};

export const computePollingInterval = (data) => {
  const status = getOrderStatus(data);
  return isTerminalOrderStatus(status) ? false : 3000;
};

export const isPollingEnabled = (orderId, initialData) => {
  if (!orderId) return false;
  const status = getOrderStatus(initialData);
  return !isTerminalOrderStatus(status);
};
