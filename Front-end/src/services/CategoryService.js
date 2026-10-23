import api from "../utils/axiosConfig";

export const addCategory = async (category) => {
  const response = await api.post("/admin/categories", category);
  return response.data;
};

export const deleteCategory = async (categoryId) => {
  const response = await api.delete(`/admin/categories/${categoryId}`);
  return response.data;
};

export const updateCategory = async (categoryId, formData) => {
  const response = await api.put(`/admin/categories/${categoryId}`, formData);
  return response.data;
};

export const fetchCategories = async () => {
  const response = await api.get("/categories");
  return response.data;
};
