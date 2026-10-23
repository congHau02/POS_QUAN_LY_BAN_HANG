import { useState, useMemo, useCallback } from "react";
import toast from "react-hot-toast";
import { useUsers } from "./useUsers";
import { useDeleteUser } from "./useDeleteUser";
import { useCurrentUser } from "../../hooks/useCurrentUser";
import { isSelfAccount } from "./userUtils";
import UserItem from "./UserItem";
import ConfirmDeleteModal from "../../ui/ConfirmDeleteModal";
import EditUserModal from "./EditUserModal";
import Spinner from "../../ui/Spinner";
import { Search, Users as UsersIcon } from "lucide-react";

function UsersList() {
  const { isLoading, users } = useUsers();
  const { isDeleting, deleteUser } = useDeleteUser();
  const { user: currentUser } = useCurrentUser();
  const [searchTerm, setSearchTerm] = useState("");
  const [userToDelete, setUserToDelete] = useState(null);
  const [userToEdit, setUserToEdit] = useState(null);

  const filteredUsers = useMemo(() => {
    if (!users) return [];
    const term = searchTerm.toLowerCase().trim();
    if (!term) return users;
    return users.filter((user) =>
      user?.name?.toLowerCase().trim().includes(term),
    );
  }, [users, searchTerm]);

  const handleEdit = useCallback((user) => {
    setUserToEdit(user);
  }, []);

  const handleDelete = useCallback(
    (user) => {
      if (isSelfAccount(user, currentUser)) {
        toast.error("Không thể tự xóa tài khoản đang đăng nhập");
        return;
      }
      setUserToDelete(user);
    },
    [currentUser]
  );

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Search Header */}
      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          placeholder="Tìm kiếm người dùng..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full pl-9 pr-4 py-2 text-sm rounded-lg border border-slate-300 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
        />
      </div>

      {/* List Scroll Area */}
      <div className="flex-1 overflow-y-auto space-y-3 pr-1">
        {isLoading ? (
          <div className="flex justify-center items-center py-12">
            <Spinner size={32} className="text-blue-600" />
          </div>
        ) : filteredUsers.length === 0 ? (
          <div className="text-center py-12 text-slate-400">
            <UsersIcon size={36} className="mx-auto mb-2 text-slate-300" />
            <p className="text-sm">Không tìm thấy người dùng</p>
          </div>
        ) : (
          filteredUsers.map((user, index) => (
            <UserItem
              key={user.userId || index}
              user={user}
              currentUser={currentUser}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          ))
        )}
      </div>

      {userToEdit && (
        <EditUserModal
          isOpen={Boolean(userToEdit)}
          onClose={() => setUserToEdit(null)}
          user={userToEdit}
        />
      )}

      {userToDelete && (
        <ConfirmDeleteModal
          isOpen={Boolean(userToDelete)}
          onClose={() => setUserToDelete(null)}
          onConfirm={() => {
            if (isSelfAccount(userToDelete, currentUser)) {
              toast.error("Không thể tự xóa tài khoản đang đăng nhập");
              setUserToDelete(null);
              return;
            }
            deleteUser(userToDelete.userId, {
              onSettled: () => setUserToDelete(null),
            });
          }}
          title="Xóa người dùng"
          entityName={userToDelete.name}
          message="Bạn có chắc muốn xóa người dùng này không? Người dùng sẽ mất quyền đăng nhập và quyền hệ thống ngay lập tức."
          isLoading={isDeleting}
        />
      )}
    </div>
  );
}

export default UsersList;
