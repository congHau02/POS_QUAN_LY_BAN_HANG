import api from "../utils/axiosConfig";

export const addUser = async (user) => {
  const response = await api.post("/admin/register", user);
  return response.data;
};

export const deleteUser = async (userId) => {
  const response = await api.delete(`/admin/users/${userId}`);
  return response.data;
};

export const fetchUsers = async () => {
  const response = await api.get("/admin/users");
  return response.data;
};

export const updateUser = async (userId, userRequest) => {
  const response = await api.put(`/admin/users/${userId}`, userRequest);
  return response.data;
};
