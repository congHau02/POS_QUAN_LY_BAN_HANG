import { useForm, useFieldArray, useWatch } from "react-hook-form";
import toast from "react-hot-toast";
import { useCreateModifierGroup } from "./useCreateModifierGroup";
import Spinner from "../../ui/Spinner";
import { SlidersHorizontal, PlusCircle, Trash2 } from "lucide-react";

function ModifierGroupForm() {
  const { isCreating, createModifierGroup } = useCreateModifierGroup();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setValue,
    getValues,
    formState: { errors },
  } = useForm({
    defaultValues: {
      name: "",
      description: "",
      minSelections: 0,
      maxSelections: 1,
      modifiers: [{ name: "", priceAdjustment: 0, isDefault: true }],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control,
    name: "modifiers",
  });

  const watchedMaxSelections = useWatch({
    control,
    name: "maxSelections",
    defaultValue: 1,
  });
  const maxSel = parseInt(watchedMaxSelections, 10) || 1;
  const isSingle = maxSel === 1;
  const currentModifiers = useWatch({
    control,
    name: "modifiers",
  }) || [];

  const handleToggleDefault = (idx) => {
    const mods = getValues("modifiers") || [];
    if (isSingle) {
      const isAlreadyDefault = Boolean(mods[idx]?.isDefault);
      mods.forEach((m, i) => {
        setValue(`modifiers.${i}.isDefault`, i === idx ? !isAlreadyDefault : false, {
          shouldValidate: true,
          shouldDirty: true,
        });
      });
    } else {
      const currentDefaultCount = mods.filter((m, i) => i !== idx && Boolean(m.isDefault)).length;
      const willBeChecked = !mods[idx]?.isDefault;
      if (willBeChecked && currentDefaultCount >= maxSel) {
        toast.error(`Chỉ được chọn tối đa ${maxSel} tùy chọn mặc định`);
        return;
      }
      setValue(`modifiers.${idx}.isDefault`, willBeChecked, {
        shouldValidate: true,
        shouldDirty: true,
      });
    }
  };

  const onSubmit = (data) => {
    const validModifiers = (data.modifiers || [])
      .filter((m) => m.name && m.name.trim() !== "")
      .map((m) => ({
        name: m.name.trim(),
        priceAdjustment: parseFloat(m.priceAdjustment) || 0,
        isDefault: Boolean(m.isDefault),
      }));

    if (validModifiers.length === 0) {
      toast.error("Vui lòng thêm ít nhất một tùy chọn hợp lệ");
      return;
    }

    const payload = {
      name: data.name.trim(),
      description: data.description ? data.description.trim() : "",
      minSelections: parseInt(data.minSelections, 10) || 0,
      maxSelections: parseInt(data.maxSelections, 10) || 1,
      modifiers: validModifiers,
    };

    createModifierGroup(payload, {
      onSuccess: () => {
        reset({
          name: "",
          description: "",
          minSelections: 0,
          maxSelections: 1,
          modifiers: [{ name: "", priceAdjustment: 0, isDefault: true }],
        });
      },
    });
  };

  const onError = (errors) => {
    if (errors.name?.message) {
      toast.error(errors.name.message);
      return;
    }
    if (errors.modifiers) {
      const firstModError = Array.isArray(errors.modifiers)
        ? errors.modifiers.find((m) => m?.name || m?.priceAdjustment)
        : null;
      if (firstModError?.name?.message) {
        toast.error(firstModError.name.message);
        return;
      }
      if (firstModError?.priceAdjustment?.message) {
        toast.error(firstModError.priceAdjustment.message);
        return;
      }
    }
    const firstError = Object.values(errors)[0];
    if (firstError?.message) {
      toast.error(firstError.message);
    } else {
      toast.error("Vui lòng kiểm tra lại các trường thông tin bắt buộc");
    }
  };

  return (
    <div>
      <div className="flex items-center gap-2 pb-4 mb-4 border-b border-slate-100">
        <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
          <SlidersHorizontal size={18} />
        </div>
        <h2 className="text-base font-semibold text-slate-900">Thêm nhóm tùy chọn</h2>
      </div>

      <form onSubmit={handleSubmit(onSubmit, onError)} noValidate className="space-y-4">
        <div>
          <label htmlFor="groupName" className="block text-sm font-medium text-slate-700 mb-1">
            Tên nhóm *
          </label>
          <input
            type="text"
            id="groupName"
            placeholder="Ví dụ: Mức đường, Topping"
            {...register("name", { required: "Tên nhóm tùy chọn là bắt buộc" })}
            disabled={isCreating}
            className={`w-full rounded-lg border px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 transition-colors ${
              errors.name
                ? "border-red-500 focus:ring-red-500 bg-red-50/10"
                : "border-slate-300 focus:ring-blue-500 focus:border-blue-500"
            }`}
          />
          {errors.name && (
            <p className="text-xs text-red-600 mt-1">{errors.name.message}</p>
          )}
        </div>

        <div>
          <label htmlFor="groupDesc" className="block text-sm font-medium text-slate-700 mb-1">
            Mô tả
          </label>
          <input
            type="text"
            id="groupDesc"
            placeholder="Ví dụ: Chọn mức ngọt yêu thích"
            {...register("description")}
            disabled={isCreating}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="minSel" className="block text-sm font-medium text-slate-700 mb-1">
              Số lựa chọn tối thiểu
            </label>
            <input
              type="number"
              id="minSel"
              min={0}
              {...register("minSelections")}
              disabled={isCreating}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="maxSel" className="block text-sm font-medium text-slate-700 mb-1">
              Số lựa chọn tối đa
            </label>
            <input
              type="number"
              id="maxSel"
              min={1}
              {...register("maxSelections")}
              disabled={isCreating}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="pt-2">
          <div className="flex justify-between items-center mb-2">
            <label className="block text-sm font-semibold text-slate-800">
              Các tùy chọn *
            </label>
            <button
              type="button"
              onClick={() => append({ name: "", priceAdjustment: 0, isDefault: false })}
              disabled={isCreating}
              className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 font-medium cursor-pointer"
            >
              <PlusCircle size={14} /> Thêm tùy chọn
            </button>
          </div>

          <div className="space-y-3">
            {fields.map((field, index) => (
              <div key={field.id} className="space-y-1">
                <div className="flex gap-2 items-center">
                  <div className="flex-1">
                    <input
                      type="text"
                      placeholder="Tùy chọn (ví dụ: Trân châu)"
                      {...register(`modifiers.${index}.name`, {
                        required: "Tên tùy chọn là bắt buộc",
                      })}
                      disabled={isCreating}
                      className={`w-full rounded-lg border px-2.5 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 transition-colors ${
                        errors.modifiers?.[index]?.name
                          ? "border-red-500 focus:ring-red-500 bg-red-50/10"
                          : "border-slate-300 focus:ring-blue-500"
                      }`}
                    />
                    {errors.modifiers?.[index]?.name && (
                      <p className="text-xs text-red-600 mt-1">
                        {errors.modifiers[index].name.message}
                      </p>
                    )}
                  </div>
                  <div className="w-28">
                    <input
                      type="number"
                      placeholder="Giá cộng thêm (VNĐ)"
                      min={0}
                      {...register(`modifiers.${index}.priceAdjustment`, {
                        min: { value: 0, message: "Giá phải lớn hơn hoặc bằng 0" },
                      })}
                      disabled={isCreating}
                      className={`w-full rounded-lg border px-2.5 py-1.5 text-xs text-slate-900 focus:outline-none focus:ring-2 transition-colors ${
                        errors.modifiers?.[index]?.priceAdjustment
                          ? "border-red-500 focus:ring-red-500 bg-red-50/10"
                          : "border-slate-300 focus:ring-blue-500"
                      }`}
                    />
                    {errors.modifiers?.[index]?.priceAdjustment && (
                      <p className="text-xs text-red-600 mt-1">
                        {errors.modifiers[index].priceAdjustment.message}
                      </p>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => handleToggleDefault(index)}
                    disabled={isCreating}
                    className={`px-2.5 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition-all cursor-pointer whitespace-nowrap ${
                      currentModifiers[index]?.isDefault
                        ? "bg-amber-50 border-amber-300 text-amber-900 font-bold shadow-2xs"
                        : "bg-slate-50 border-slate-200 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                    }`}
                    title={
                      isSingle
                        ? "Chọn làm tùy chọn mặc định duy nhất (chọn 1)"
                        : "Chọn làm tùy chọn mặc định (chọn nhiều)"
                    }
                  >
                    <span
                      className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center text-[9px] ${
                        currentModifiers[index]?.isDefault
                          ? "border-amber-600 bg-amber-500 text-white"
                          : "border-slate-300 bg-white"
                      }`}
                    >
                      {currentModifiers[index]?.isDefault ? "✓" : ""}
                    </span>
                    <span>Mặc định</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      if (fields.length <= 1) {
                        toast.error("Cần có ít nhất một tùy chọn");
                        return;
                      }
                      remove(index);
                    }}
                    disabled={isCreating || fields.length === 1}
                    title="Xóa tùy chọn"
                    className="p-1.5 text-slate-400 hover:text-red-600 disabled:opacity-40 cursor-pointer self-start mt-0.5"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        <button
          type="submit"
          disabled={isCreating}
          className="w-full inline-flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg text-sm font-medium shadow-sm transition-colors disabled:opacity-50 cursor-pointer mt-2"
        >
          {isCreating ? <Spinner className="text-white" /> : "Lưu nhóm tùy chọn"}
        </button>
      </form>
    </div>
  );
}

export default ModifierGroupForm;
