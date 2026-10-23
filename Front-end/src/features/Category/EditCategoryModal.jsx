import { useState, useMemo, useEffect } from "react";
import { createPortal } from "react-dom";
import { useForm, useWatch } from "react-hook-form";
import toast from "react-hot-toast";
import { X, Edit3, Image as ImageIcon, Loader2 } from "lucide-react";
import { useUpdateCategory } from "./useUpdateCategory";
import { useLockBodyScroll } from "../../hooks/useLockBodyScroll";
import { useEscapeKey } from "../../hooks/useEscapeKey";

const DEFAULT_PREVIEW = "https://placehold.co/60x60?text=Upload";

const COLOR_PRESETS = [
  "#2563EB", // Blue
  "#7C3AED", // Purple
  "#DB2777", // Pink
  "#DC2626", // Red
  "#EA580C", // Orange
  "#D97706", // Amber
  "#059669", // Emerald
  "#0D9488", // Teal
  "#475569", // Slate
];

export default function EditCategoryModal({ isOpen, onClose, category }) {
  const { isUpdating, updateCategory } = useUpdateCategory();
  const [previewUrl, setPreviewUrl] = useState(
    () => category?.imgUrl || DEFAULT_PREVIEW
  );

  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  useLockBodyScroll(isOpen);
  useEscapeKey(() => {
    if (!isUpdating) onClose();
  }, isOpen);

  const formValues = useMemo(() => {
    if (!category) return undefined;
    return {
      name: category.name || "",
      description: category.description || "",
      bgColor: category.bgColor || "#2563EB",
    };
  }, [category]);

  const {
    register,
    control,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm({
    values: formValues,
  });

  const selectedColor = useWatch({
    control,
    name: "bgColor",
    defaultValue: category?.bgColor || "#2563EB",
  });

  if (!isOpen || !category) return null;

  const handleImageChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      if (previewUrl && previewUrl !== DEFAULT_PREVIEW && !previewUrl.startsWith("http")) {
        URL.revokeObjectURL(previewUrl);
      }
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const onSubmit = (data) => {
    const formData = new FormData();
    const categoryRequest = {
      name: data.name.trim(),
      description: data.description?.trim(),
      bgColor: data.bgColor,
    };
    formData.append("category", JSON.stringify(categoryRequest));

    const imageFile = data.imgUrl?.[0];
    if (imageFile) {
      if (imageFile.size > 5 * 1024 * 1024) {
        toast.error("Dung lượng hình ảnh không được vượt quá 5MB");
        return;
      }
      formData.append("file", imageFile);
    }

    updateCategory(
      { categoryId: category.categoryId, formData },
      {
        onSuccess: () => onClose(),
      }
    );
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
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md overflow-hidden animate-in zoom-in-95 duration-200 border border-slate-200">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Edit3 size={18} />
            </div>
            <div>
              <h3 className="text-base font-semibold text-slate-900">Chỉnh sửa danh mục</h3>
              <p className="text-xs text-slate-500">Cập nhật thông tin và giao diện danh mục</p>
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

        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Hình ảnh danh mục
            </label>
            <label
              htmlFor="editCategoryImg"
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
                <p className="text[11px] text-slate-400">Để trống nếu muốn giữ nguyên ảnh hiện tại</p>
              </div>
              <input
                type="file"
                id="editCategoryImg"
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

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Tên danh mục <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              disabled={isUpdating}
              placeholder="Nhập tên danh mục..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100"
              {...register("name", {
                required: "Tên danh mục không được để trống",
                minLength: { value: 2, message: "Tên danh mục tối thiểu 2 ký tự" },
              })}
            />
            {errors.name && (
              <p className="text-xs text-red-500 mt-1">{errors.name.message}</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Mô tả danh mục
            </label>
            <textarea
              rows={2}
              disabled={isUpdating}
              placeholder="Nhập mô tả danh mục không bắt buộc..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors disabled:bg-slate-100 resize-none"
              {...register("description")}
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Màu điểm nhấn
            </label>
            <div className="flex items-center gap-2 mb-2">
              <input
                type="color"
                disabled={isUpdating}
                value={selectedColor || "#2563EB"}
                onChange={(e) => setValue("bgColor", e.target.value)}
                className="w-8 h-8 rounded-lg border border-slate-200 cursor-pointer p-0.5"
              />
              <input
                type="text"
                disabled={isUpdating}
                className="w-28 px-2.5 py-1.5 text-xs uppercase font-mono rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
                {...register("bgColor")}
              />
            </div>
            <div className="flex flex-wrap gap-1.5">
              {COLOR_PRESETS.map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setValue("bgColor", color)}
                  className={`w-6 h-6 rounded-full border transition-transform hover:scale-110 cursor-pointer ${
                    selectedColor === color
                      ? "border-slate-800 ring-2 ring-blue-500 ring-offset-1 scale-105"
                      : "border-black/10"
                  }`}
                  style={{ backgroundColor: color }}
                  title={color}
                />
              ))}
            </div>
          </div>

          <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-100">
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
