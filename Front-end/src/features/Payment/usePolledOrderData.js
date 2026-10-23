import { useQuery } from "@tanstack/react-query";
import { getOrderById } from "../../services/OrderService";
import {
  isTerminalOrderStatus,
  getOrderStatus,
  computePollingInterval,
  isPollingEnabled,
} from "./orderTerminalState";

export {
  isTerminalOrderStatus,
  getOrderStatus,
  computePollingInterval,
  isPollingEnabled,
};

export function usePolledOrderData(baseData, orderId) {
  const isEnabled = isPollingEnabled(orderId, baseData);

  const { data: polledOrderData } = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => getOrderById(orderId),
    initialData: baseData || undefined,
    enabled: isEnabled,
    refetchInterval: (query) => {
      return computePollingInterval(query);
    },
  });

  return { polledOrderData };
}
