import api from "../utils/axiosConfig";

export const addItem = async (item) => {
  const response = await api.post("/admin/items", item);
  return response.data;
};

export const updateItem = async (itemId, itemData) => {
  const isFormData = itemData instanceof FormData;
  const config = isFormData
    ? { headers: { "Content-Type": "multipart/form-data" } }
    : { headers: { "Content-Type": "application/json" } };
  const response = await api.put(`/admin/items/${itemId}`, itemData, config);
  return response.data;
};

export const deleteItem = async (itemId) => {
  const response = await api.delete(`/admin/items/${itemId}`);
  return response.data;
};

export const fetchItems = async () => {
  const response = await api.get("/items");
  return response.data;
};

export const attachModifierGroups = async (itemId, groupIds) => {
  const response = await api.post(`/admin/items/${itemId}/modifier-groups`, groupIds);
  return response.data;
};

export const fetchItemModifierGroups = async (itemId) => {
  const response = await api.get(`/items/${itemId}/modifier-groups`);
  return response.data;
};
