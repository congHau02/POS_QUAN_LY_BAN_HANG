import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { fetchCustomers } from "../../services/CustomerService";

export function useCustomers(query = "", page = 0, size = 20) {
  const { isPending: isLoading, isFetching, data: customersData, error } = useQuery({
    queryKey: ["customers", query, page, size],
    queryFn: () => fetchCustomers(query, page, size),
    placeholderData: keepPreviousData,
    staleTime: 30000,
  });

  const customerList = Array.isArray(customersData)
    ? customersData
    : (customersData?.content || []);

  return { isLoading, isFetching, error, customers: customerList, pagination: customersData };
}
