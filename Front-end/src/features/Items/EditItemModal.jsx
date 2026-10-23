import { useState, useMemo, useEffect } from "react";
import { createPortal } from "react-dom";
import { useForm, useFieldArray, useWatch } from "react-hook-form";
import toast from "react-hot-toast";
import { useUpdateItem } from "./useUpdateItem";
import { useCategories } from "../Category/useCategories";
import { useModifierGroups } from "../Modifiers/useModifierGroups";
import { createDefaultVariant } from "../../utils/variantDefaults";
import { useLockBodyScroll } from "../../hooks/useLockBodyScroll";
import { useEscapeKey } from "../../hooks/useEscapeKey";
import {
  X,
  Edit3,
  Image as ImageIcon,
  PlusCircle,
  Trash2,
  CheckSquare,
  Square,
  Loader2,
} from "lucide-react";

const DEFAULT_PREVIEW = "https://placehold.co/60x60?text=Upload";

function VariantAttributes({ control, vIndex, register, disabled }) {
  const { fields, append, remove } = useFieldArray({
    control,
    name: `variants.${vIndex}.attributes`,
  });

  return (
    <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-200 mt-2">
      <div className="flex justify-between items-center mb-1.5">
        <span className="text-xs font-semibold text-slate-500">
          Thuộc tính (JSON)
        </span>
        <button
          type="button"
          className="text-xs text-blue-600 hover:text-blue-700 font-medium cursor-pointer"
          onClick={() => append({ key: "", value: "" })}
          disabled={disabled}
        >
          + Thêm thuộc tính
        </button>
      </div>
      <div className="space-y-1.5">
        {fields.map((field, aIndex) => (
          <div
            key={field.id}
            className="grid grid-cols-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-1.5 items-center"
          >
            <input
              type="text"
              placeholder="Tên (ví dụ: Kích thước)"
              {...register(`variants.${vIndex}.attributes.${aIndex}.key`)}
              disabled={disabled}
              className="min-w-0 w-full rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-900 placeholder-slate-400 bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <input
              type="text"
              placeholder="Giá trị (ví dụ: M)"
              {...register(`variants.${vIndex}.attributes.${aIndex}.value`)}
              disabled={disabled}
              className="min-w-0 w-full rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-900 placeholder-slate-400 bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={() => remove(aIndex)}
              disabled={disabled}
              className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              <X size={14} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function EditItemModal({ isOpen, onClose, item }) {
  const { isLoading: isCategoriesLoading, categories } = useCategories();
  const { modifierGroups } = useModifierGroups();
  const { isUpdating, updateItem } = useUpdateItem();
  const [previewUrl, setPreviewUrl] = useState(
    () => item?.imgUrl || DEFAULT_PREVIEW
  );

  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);
  const [selectedModifierGroupIds, setSelectedModifierGroupIds] = useState(
    () => (item?.modifierGroups || []).map((g) => g.groupId)
  );

  useLockBodyScroll(isOpen);
  useEscapeKey(() => {
    if (!isUpdating) onClose();
  }, isOpen);

  const formValues = useMemo(() => {
    if (!item) return undefined;
    const hasVariants = Boolean(item.variants && item.variants.length > 0);
    const variants = hasVariants
      ? item.variants.map((v) => ({
          sku: v.sku || "",
          basePrice: v.basePrice !== undefined ? String(v.basePrice) : "",
          attributes: Object.entries(v.attributes || {}).map(([key, value]) => ({
            key,
            value,
          })),
        }))
      : [createDefaultVariant()];

    return {
      name: item.name || "",
      description: item.description || "",
      price: item.price !== undefined ? String(item.price) : "",
      categoryId: item.categoryId || "",
      hasVariants,
      variants,
    };
  }, [item]);

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm({
    values: formValues,
  });

  const hasVariants = useWatch({ control, name: "hasVariants" });

  const {
    fields: variantFields,
    append: appendVariant,
    remove: removeVariant,
  } = useFieldArray({
    control,
    name: "variants",
  });

  if (!isOpen || !item) return null;

  const handleToggleModifierGroup = (groupId) => {
    setSelectedModifierGroupIds((prev) =>
      prev.includes(groupId)
        ? prev.filter((id) => id !== groupId)
        : [...prev, groupId]
    );
  };

  const handleImageChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      if (
        previewUrl &&
        previewUrl !== DEFAULT_PREVIEW &&
        !previewUrl.startsWith("http")
      ) {
        URL.revokeObjectURL(previewUrl);
      }
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const onSubmit = (data) => {
    const formData = new FormData();

    let itemVariants = null;
    if (data.hasVariants) {
      if (!data.variants || data.variants.length === 0) {
        toast.error("Cần có ít nhất một biến thể khi bật tùy chọn biến thể");
        return;
      }

      for (let i = 0; i < data.variants.length; i++) {
        const v = data.variants[i];
        if (!v.sku || !v.sku.trim()) {
          toast.error(`Biến thể số ${i + 1} cần có mã SKU`);
          return;
        }
        if (!v.basePrice || parseFloat(v.basePrice) < 0) {
          toast.error(`Biến thể số ${i + 1} cần có giá cơ bản hợp lệ`);
          return;
        }
      }

      itemVariants = data.variants.map((v) => {
        const attributesMap = {};
        (v.attributes || []).forEach((attr) => {
          if (attr.key && attr.value && attr.key.trim() && attr.value.trim()) {
            attributesMap[attr.key.trim()] = attr.value.trim();
          }
        });
        return {
          sku: v.sku.trim(),
          basePrice: parseFloat(v.basePrice),
          attributes: attributesMap,
        };
      });
    }

    const itemRequest = {
      name: data.name.trim(),
      description: data.description ? data.description.trim() : "",
      price: data.hasVariants
        ? parseFloat(data.variants[0].basePrice) || 0
        : parseFloat(data.price) || 0,
      categoryId: data.categoryId,
      variants: itemVariants,
      modifierGroupIds: selectedModifierGroupIds,
    };

    formData.append("item", JSON.stringify(itemRequest));

    const imageFile = data.imgUrl?.[0];
    if (imageFile) {
      if (imageFile.size > 5 * 1024 * 1024) {
        toast.error("Dung lượng hình ảnh không được vượt quá 5MB");
        return;
      }
      formData.append("file", imageFile);
    }

    updateItem(
      { itemId: item.itemId, itemData: formData },
      {
        onSuccess: () => onClose(),
      }
    );
  };

  const onError = (errors) => {
    function getFirstMessage(err) {
      if (!err) return null;
      if (typeof err === "object") {
        if (err.message && typeof err.message === "string") return err.message;
        for (const key of Object.keys(err)) {
          const res = getFirstMessage(err[key]);
          if (res) return res;
        }
      }
      return null;
    }
    const message =
      getFirstMessage(errors) || "Vui lòng kiểm tra lại các trường bắt buộc";
    toast.error(message);
  };

  const modalContent = (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isUpdating) onClose();
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200 border border-slate-200">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 flex-shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Edit3 size={18} />
            </div>
            <div>
              <h3 className="text-base font-semibold text-slate-900">
                Chỉnh sửa mặt hàng
              </h3>
              <p className="text-xs text-slate-500">
                Cập nhật thông tin, biến thể và tùy chọn của sản phẩm
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isUpdating}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors disabled:opacity-50 cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        <form
          onSubmit={handleSubmit(onSubmit, onError)}
          noValidate
          className="flex-1 overflow-y-auto p-6 space-y-4"
        >
          {/* Image Upload Area */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Hình ảnh mặt hàng
            </label>
            <label
              htmlFor="editItemImg"
              className={`block border-2 border-dashed rounded-xl p-3 text-center transition-colors ${
                isUpdating
                  ? "border-slate-200 opacity-60 cursor-not-allowed bg-slate-50"
                  : "border-slate-300 hover:border-blue-500 bg-slate-50/50 hover:bg-blue-50/20 cursor-pointer"
              }`}
            >
              <div className="flex flex-col items-center justify-center gap-1">
                <img
                  src={previewUrl}
                  alt="preview"
                  loading="lazy"
                  decoding="async"
                  width={64}
                  height={64}
                  className="w-16 h-16 rounded-lg object-cover shadow-xs border border-slate-200 bg-white"
                />
                <div className="flex items-center gap-1 text-xs text-slate-500 mt-1">
                  <ImageIcon size={14} className="text-slate-400" />
                  <span>Chọn ảnh mới để thay đổi (tối đa 5MB)</span>
                </div>
                <p className="text-[11px] text-slate-400">
                  Để trống nếu muốn giữ nguyên ảnh hiện tại
                </p>
              </div>
              <input
                type="file"
                id="editItemImg"
                accept="image/*"
                disabled={isUpdating}
                className="hidden"
                {...register("imgUrl")}
                onChange={(e) => {
                  register("imgUrl").onChange(e);
                  handleImageChange(e);
                }}
              />
            </label>
          </div>

          {/* Item Name */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Tên mặt hàng <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              disabled={isUpdating}
              placeholder="Nhập tên mặt hàng..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100"
              {...register("name", {
                required: "Tên mặt hàng là bắt buộc",
                minLength: { value: 2, message: "Tên tối thiểu 2 ký tự" },
              })}
            />
            {errors.name && (
              <p className="text-xs text-red-500 mt-1">{errors.name.message}</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Mô tả mặt hàng
            </label>
            <textarea
              rows={2}
              disabled={isUpdating}
              placeholder="Nhập mô tả sản phẩm..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100 resize-none"
              {...register("description")}
            />
          </div>

          {/* Category Selection */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Danh mục <span className="text-red-500">*</span>
            </label>
            <select
              disabled={isUpdating || isCategoriesLoading}
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100"
              {...register("categoryId", {
                required: "Danh mục là bắt buộc",
              })}
            >
              <option value="">-- Chọn danh mục --</option>
              {categories?.map((cat) => (
                <option key={cat.categoryId} value={cat.categoryId}>
                  {cat.name}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <p className="text-xs text-red-500 mt-1">
                {errors.categoryId.message}
              </p>
            )}
          </div>

          {/* Variants Checkbox Toggle */}
          <div className="pt-2 border-t border-slate-100">
            <label className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                {...register("hasVariants")}
                disabled={isUpdating}
                className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
              />
              <span className="text-sm font-medium text-slate-800">
                Sản phẩm có nhiều biến thể (Size, Màu sắc,...)
              </span>
            </label>
          </div>

          {/* Price (If no variants) */}
          {!hasVariants && (
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Giá bán (VNĐ) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                step="any"
                disabled={isUpdating}
                placeholder="0"
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100"
                {...register("price", {
                  required: !hasVariants ? "Giá bán là bắt buộc" : false,
                  min: { value: 0, message: "Giá bán không được âm" },
                })}
              />
              {errors.price && (
                <p className="text-xs text-red-500 mt-1">
                  {errors.price.message}
                </p>
              )}
            </div>
          )}

          {/* Variants List Section */}
          {hasVariants && (
            <div className="space-y-3 p-3.5 bg-slate-50 rounded-xl border border-slate-200">
              <div className="flex justify-between items-center">
                <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Danh sách biến thể ({variantFields.length})
                </span>
                <button
                  type="button"
                  onClick={() => appendVariant(createDefaultVariant())}
                  disabled={isUpdating}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 cursor-pointer"
                >
                  <PlusCircle size={14} /> Thêm biến thể
                </button>
              </div>

              {variantFields.map((field, vIndex) => (
                <div
                  key={field.id}
                  className="p-3 bg-white rounded-lg border border-slate-200 shadow-2xs space-y-2.5"
                >
                  <div className="flex justify-between items-center">
                    <span className="text-xs font-bold text-slate-800">
                      Biến thể #{vIndex + 1}
                    </span>
                    {variantFields.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeVariant(vIndex)}
                        disabled={isUpdating}
                        className="text-red-500 hover:text-red-700 p-1 cursor-pointer"
                        title="Xóa biến thể"
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[11px] font-semibold text-slate-600 mb-0.5">
                        Mã SKU *
                      </label>
                      <input
                        type="text"
                        placeholder="VD: AO-THUN-DEN-L"
                        {...register(`variants.${vIndex}.sku`, {
                          required: "SKU là bắt buộc",
                        })}
                        disabled={isUpdating}
                        className="w-full px-2.5 py-1.5 text-xs rounded-md border border-slate-300 focus:outline-none focus:ring-1 focus:ring-blue-500 uppercase font-mono"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-semibold text-slate-600 mb-0.5">
                        Giá cơ bản (VNĐ) *
                      </label>
                      <input
                        type="number"
                        step="any"
                        placeholder="0"
                        {...register(`variants.${vIndex}.basePrice`, {
                          required: "Giá cơ bản là bắt buộc",
                          min: { value: 0, message: "Giá không được âm" },
                        })}
                        disabled={isUpdating}
                        className="w-full px-2.5 py-1.5 text-xs rounded-md border border-slate-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
                      />
                    </div>
                  </div>

                  <VariantAttributes
                    control={control}
                    vIndex={vIndex}
                    register={register}
                    disabled={isUpdating}
                  />
                </div>
              ))}
            </div>
          )}

          {/* Modifier Groups Section */}
          {modifierGroups && modifierGroups.length > 0 && (
            <div className="pt-2 border-t border-slate-100">
              <label className="block text-xs font-semibold text-slate-700 mb-2">
                Nhóm tùy chọn đính kèm (Modifier Groups)
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {modifierGroups.map((group) => {
                  const isSelected = selectedModifierGroupIds.includes(
                    group.groupId
                  );
                  return (
                    <div
                      key={group.groupId}
                      onClick={() =>
                        !isUpdating && handleToggleModifierGroup(group.groupId)
                      }
                      className={`flex items-start gap-2.5 p-2.5 rounded-lg border text-xs cursor-pointer transition-colors ${
                        isSelected
                          ? "border-blue-500 bg-blue-50/40 text-blue-900"
                          : "border-slate-200 bg-white hover:bg-slate-50 text-slate-700"
                      }`}
                    >
                      {isSelected ? (
                        <CheckSquare
                          size={16}
                          className="text-blue-600 shrink-0 mt-0.5"
                        />
                      ) : (
                        <Square
                          size={16}
                          className="text-slate-400 shrink-0 mt-0.5"
                        />
                      )}
                      <div>
                        <div className="font-semibold">{group.name}</div>
                        <div className="text-[11px] text-slate-500 mt-0.5">
                          {group.modifiers?.length || 0} tùy chọn (chọn{" "}
                          {group.minSelections} - {group.maxSelections})
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Modal Actions */}
          <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-100 flex-shrink-0">
            <button
              type="button"
              onClick={onClose}
              disabled={isUpdating}
              className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50 cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isUpdating}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-xs transition-colors flex items-center gap-2 disabled:opacity-60 cursor-pointer"
            >
              {isUpdating && <Loader2 size={16} className="animate-spin" />}
              <span>{isUpdating ? "Đang lưu..." : "Lưu thay đổi"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
}
