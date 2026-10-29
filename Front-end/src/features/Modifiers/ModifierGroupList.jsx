import { useState } from "react";
import Spinner from "../../ui/Spinner";
import ConfirmDeleteModal from "../../ui/ConfirmDeleteModal";
import { formatCurrency } from "../../utils/formatCurrency";
import { useModifierGroups } from "./useModifierGroups";
import { useDeleteModifierGroup } from "./useDeleteModifierGroup";
import { Search, Trash2, SlidersHorizontal } from "lucide-react";

function ModifierGroupList() {
  const { modifierGroups, isLoading } = useModifierGroups();
  const { isDeleting, deleteModifierGroup } = useDeleteModifierGroup();
  const [searchTerm, setSearchTerm] = useState("");
  const [groupToDelete, setGroupToDelete] = useState(null);

  const filteredGroups = modifierGroups?.filter((group) =>
    group.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Search Header */}
      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          placeholder="Tìm kiếm nhóm tùy chọn..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full pl-9 pr-4 py-2 text-sm rounded-lg border border-slate-300 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
        />
      </div>

      {/* List Container */}
      <div className="flex-1 overflow-y-auto space-y-3 pr-1">
        {isLoading ? (
          <div className="flex justify-center items-center py-12">
            <Spinner size={32} className="text-blue-600" />
          </div>
        ) : filteredGroups?.length === 0 ? (
          <div className="text-center py-12 text-slate-400">
            <SlidersHorizontal size={36} className="mx-auto mb-2 text-slate-300" />
            <p className="text-sm">Không tìm thấy nhóm tùy chọn</p>
          </div>
        ) : (
          filteredGroups?.map((group) => (
            <div
              key={group.groupId}
              className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs hover:shadow-sm transition-all"
            >
              <div className="flex justify-between items-start gap-2">
                <div>
                  <h4 className="text-sm font-semibold text-slate-900">{group.name}</h4>
                  {group.description && (
                    <p className="text-xs text-slate-500 mt-0.5 mb-2">
                      {group.description}
                    </p>
                  )}
                  <div className="flex items-center gap-2 mt-1 mb-2">
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                      Tối thiểu: {group.minSelections}
                    </span>
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                      Tối đa: {group.maxSelections}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50 cursor-pointer"
                  onClick={() => setGroupToDelete(group)}
                  disabled={isDeleting}
                  title="Xóa nhóm"
                >
                  <Trash2 size={15} />
                </button>
              </div>

              {/* Options */}
              <div className="mt-2 pt-2 border-t border-slate-100">
                <span className="text-[11px] font-medium text-slate-400 uppercase tracking-wider block mb-1.5">
                  Tùy chọn:
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {group.modifiers?.map((mod) => (
                    <span
                      key={mod.modifierId}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium bg-blue-50 text-blue-800 border border-blue-100"
                    >
                      <span>{mod.name}</span>
                      {Boolean(mod.isDefault) && (
                        <span className="px-1 py-0.2 rounded text-[10px] font-bold bg-amber-200 text-amber-900 border border-amber-300">
                          Mặc định
                        </span>
                      )}
                      {mod.priceAdjustment > 0 && (
                        <span className="text-blue-600 font-semibold ml-0.5">
                          (+{formatCurrency(mod.priceAdjustment)})
                        </span>
                      )}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      <ConfirmDeleteModal
        isOpen={Boolean(groupToDelete)}
        onClose={() => setGroupToDelete(null)}
        onConfirm={() => {
          if (groupToDelete) {
            deleteModifierGroup(groupToDelete.groupId, {
              onSettled: () => setGroupToDelete(null),
            });
          }
        }}
        title="Xóa nhóm tùy chọn"
        entityName={groupToDelete?.name || ""}
        message="Bạn có chắc muốn xóa nhóm tùy chọn này không? Các tùy chọn liên kết với mặt hàng sẽ bị gỡ."
        isLoading={isDeleting}
      />
    </div>
  );
}

export default ModifierGroupList;
