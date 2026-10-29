import { useState, useEffect, useRef } from "react";
import toast from "react-hot-toast";
import { formatCurrency } from "../../utils/formatCurrency";
import {
  isModifierGroupMandatory,
  validateModifierSelections,
  getModifierGroupBadgeText,
  getDefaultModifiersMap,
} from "../../utils/modifierValidation";
import { X, Minus, Plus } from "lucide-react";

function POSItemModal({ item, isOpen, onClose, onAddToCart }) {
  const hasVariants = Boolean(item?.variants && item.variants.length > 0);
  const hasModifiers = Boolean(item?.modifierGroups && item.modifierGroups.length > 0);

  // Selected variant state (defaults to first variant if available)
  const [selectedVariantId, setSelectedVariantId] = useState(() => {
    return hasVariants ? item.variants[0]?.variantId : null;
  });

  // Selected modifiers state: Map of groupId -> Set of modifierIds (defaults pre-filled)
  const [selectedModifiersMap, setSelectedModifiersMap] = useState(() => {
    return hasModifiers ? getDefaultModifiersMap(item.modifierGroups) : {};
  });
  const [quantity, setQuantity] = useState(1);

  // Adjust state during render when item changes without cascading effect
  const [prevItemId, setPrevItemId] = useState(item?.itemId);
  if (item?.itemId !== prevItemId) {
    setPrevItemId(item?.itemId);
    setSelectedVariantId(hasVariants ? item.variants[0]?.variantId : null);
    setSelectedModifiersMap(hasModifiers ? getDefaultModifiersMap(item.modifierGroups) : {});
    setQuantity(1);
  }

  // Active selected variant object
  const activeVariant = hasVariants
    ? item.variants.find((v) => v.variantId === selectedVariantId) || item.variants[0]
    : null;

  // Calculate base price
  const basePrice = activeVariant
    ? Number(activeVariant.basePrice)
    : Number(item?.price || 0);

  // Selected modifiers list
  const selectedModifiersList = [];
  if (hasModifiers && item.modifierGroups) {
    item.modifierGroups.forEach((group) => {
      const selectedIds = selectedModifiersMap[group.groupId];
      if (selectedIds && group.modifiers) {
        group.modifiers.forEach((mod) => {
          if (selectedIds.has(mod.modifierId)) {
            selectedModifiersList.push({
              modifierId: mod.modifierId,
              name: mod.name,
              priceAdjustment: Number(mod.priceAdjustment || 0),
            });
          }
        });
      }
    });
  }

  // Dynamic unit price: basePrice + sum(selected modifiers price adjustments)
  const modifiersAdjustment = selectedModifiersList.reduce(
    (sum, m) => sum + m.priceAdjustment,
    0
  );
  const unitPrice = basePrice + modifiersAdjustment;
  const totalLinePrice = unitPrice * quantity;

  const handleModifierToggle = (group, modifier) => {
    setSelectedModifiersMap((prev) => {
      const currentSelected = new Set(prev[group.groupId] || []);
      const isSingleSelect = group.maxSelections === 1;
      const isMandatory = isModifierGroupMandatory(group);

      if (isSingleSelect) {
        if (currentSelected.has(modifier.modifierId)) {
          // Allow deselecting single-choice only if group is not mandatory
          if (!isMandatory) {
            currentSelected.clear();
          }
        } else {
          currentSelected.clear();
          currentSelected.add(modifier.modifierId);
        }
      } else {
        if (currentSelected.has(modifier.modifierId)) {
          currentSelected.delete(modifier.modifierId);
        } else {
          const max = group.maxSelections || Infinity;
          if (currentSelected.size < max) {
            currentSelected.add(modifier.modifierId);
          }
        }
      }

      return {
        ...prev,
        [group.groupId]: currentSelected,
      };
    });
  };

  const handleAdd = () => {
    if (hasModifiers && item.modifierGroups) {
      const validation = validateModifierSelections(
        item.modifierGroups,
        selectedModifiersMap
      );
      if (!validation.isValid) {
        toast.error(validation.errorMessage);
        return;
      }
    }

    const variantLabel = activeVariant?.attributes
      ? Object.entries(activeVariant.attributes)
          .map(([k, v]) => `${k}: ${v}`)
          .join(", ")
      : activeVariant?.sku || null;

    const modKey = selectedModifiersList
      .map((m) => m.modifierId)
      .sort()
      .join("-");
    const cartItemId = `${item.itemId}_${activeVariant?.variantId || "default"}_${modKey}`;

    onAddToCart({
      cartItemId,
      itemId: item.itemId,
      variantId: activeVariant?.variantId || null,
      variantSku: activeVariant?.sku || null,
      variantLabel,
      name: item.name,
      basePrice,
      price: unitPrice,
      quantity,
      selectedModifiers: selectedModifiersList,
    });

    onClose();
  };

  const handleAddRef = useRef(handleAdd);
  useEffect(() => {
    handleAddRef.current = handleAdd;
  });

  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCloseRef.current?.();
      } else if (e.key === "Enter") {
        const tagName = document.activeElement?.tagName?.toLowerCase();
        if (tagName !== "input" && tagName !== "textarea") {
          e.preventDefault();
          handleAddRef.current?.();
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  if (!isOpen || !item) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs" 
        onClick={onClose}
      />

      {/* Modal Card */}
      <div className="relative w-full max-w-xl bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden z-10 flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            {item.imgUrl && (
              <img
                src={item.imgUrl}
                alt={item.name}
                loading="lazy"
                decoding="async"
                width={48}
                height={48}
                className="w-12 h-12 rounded-lg object-cover border border-slate-200"
              />
            )}
            <div>
              <h3 className="text-base font-bold text-slate-900 leading-tight">
                {item.name}
              </h3>
              <span className="text-xs text-slate-500">
                Danh mục: {item.categoryName || "Chung"}
              </span>
            </div>
          </div>
          <button
            type="button"
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-5 overflow-y-auto space-y-4 flex-1">
          {/* 1. Variant Selection */}
          {hasVariants && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2">
                Chọn biến thể / kích thước:
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {item.variants.map((variant) => {
                  const isSelected =
                    (activeVariant?.variantId || item.variants[0]?.variantId) ===
                    variant.variantId;
                  const attrSummary = variant.attributes
                    ? Object.entries(variant.attributes)
                        .map(([k, v]) => `${k}: ${v}`)
                        .join(" | ")
                    : variant.sku;
                  const inStock = (variant.cachedStockQuantity ?? 0) > 0;

                  return (
                    <div
                      key={variant.variantId}
                      className={`p-3 rounded-xl border transition-all cursor-pointer flex justify-between items-center ${
                        isSelected
                          ? "border-blue-600 bg-blue-50/60 shadow-xs"
                          : "border-slate-200 hover:border-slate-300 hover:bg-slate-50 bg-white"
                      }`}
                      onClick={() => setSelectedVariantId(variant.variantId)}
                    >
                      <div>
                        <div className={`text-xs font-bold ${isSelected ? "text-blue-900" : "text-slate-900"}`}>
                          {attrSummary || variant.sku}
                        </div>
                        <div className="text-[11px] text-slate-400 font-mono">
                          SKU: {variant.sku}
                        </div>
                        <div className="mt-1">
                          <span
                            className={`inline-flex items-center px-1.5 py-0.2 rounded-full text-[10px] font-semibold ${
                              inStock
                                ? "bg-emerald-100 text-emerald-800"
                                : "bg-red-100 text-red-800"
                            }`}
                          >
                            Tồn kho: {variant.cachedStockQuantity ?? 0}
                          </span>
                        </div>
                      </div>
                      <span className={`text-xs font-bold ${isSelected ? "text-blue-700" : "text-slate-800"}`}>
                        {formatCurrency(variant.basePrice)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* 2. Modifiers Selection */}
          {hasModifiers && (
            <div className="space-y-3">
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider">
                Tùy chỉnh lựa chọn & món thêm:
              </label>
              {item.modifierGroups.map((group) => {
                const selectedSet =
                  selectedModifiersMap[group.groupId] || new Set();
                const isSingleSelect = group.maxSelections === 1;
                const isMandatory = isModifierGroupMandatory(group);
                const badgeText = getModifierGroupBadgeText(group);
                const isUnmet =
                  isMandatory && selectedSet.size < Number(group.minSelections);

                return (
                  <div
                    key={group.groupId}
                    className={`p-3.5 rounded-xl border space-y-2 transition-colors ${
                      isUnmet
                        ? "bg-amber-50/30 border-amber-300"
                        : "bg-slate-50 border-slate-200"
                    }`}
                  >
                    <div className="flex justify-between items-center gap-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs font-bold text-slate-800">
                          {group.name}
                        </span>
                        {badgeText && (
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
                            {badgeText}
                          </span>
                        )}
                      </div>
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-slate-200 text-slate-700 shrink-0">
                        {isSingleSelect
                          ? "Chọn 1"
                          : `Tối đa ${group.maxSelections || "không giới hạn"}`}
                      </span>
                    </div>
                    {group.description && (
                      <p className="text-[11px] text-slate-500">
                        {group.description}
                      </p>
                    )}

                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {group.modifiers?.map((mod) => {
                        const isModSelected = selectedSet.has(mod.modifierId);
                        return (
                          <button
                            key={mod.modifierId}
                            type="button"
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors cursor-pointer flex items-center gap-1.5 ${
                              isModSelected
                                ? "bg-blue-600 border-blue-600 text-white shadow-xs"
                                : "bg-white border-slate-300 text-slate-700 hover:bg-slate-100"
                            }`}
                            onClick={() => handleModifierToggle(group, mod)}
                          >
                            <span>{mod.name}</span>
                            {Boolean(mod.isDefault) && (
                              <span
                                className={`text-[10px] px-1.5 py-0.2 rounded font-semibold ${
                                  isModSelected
                                    ? "bg-blue-500 text-white"
                                    : "bg-slate-100 text-slate-600 border border-slate-200"
                                }`}
                              >
                                Mặc định
                              </span>
                            )}
                            {mod.priceAdjustment > 0 && (
                              <span className={`ml-0.5 text-[11px] ${isModSelected ? "text-blue-100" : "text-blue-600"}`}>
                                (+{formatCurrency(mod.priceAdjustment)})
                              </span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* 3. Quantity Selector */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
            <span className="text-xs font-bold text-slate-800">Số lượng:</span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className="w-7 h-7 rounded-md border border-slate-300 bg-white hover:bg-slate-100 flex items-center justify-center text-slate-600 disabled:opacity-40 cursor-pointer"
                disabled={quantity <= 1}
                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
              >
                <Minus size={13} />
              </button>
              <span className="text-sm font-bold text-slate-900 w-8 text-center">
                {quantity}
              </span>
              <button
                type="button"
                className="w-7 h-7 rounded-md border border-slate-300 bg-white hover:bg-slate-100 flex items-center justify-center text-slate-600 cursor-pointer"
                onClick={() => setQuantity((q) => q + 1)}
              >
                <Plus size={13} />
              </button>
            </div>
          </div>

          {/* Price Breakdown */}
          <div className="flex justify-between items-center text-xs text-slate-500 pt-1">
            <span>
              Đơn giá: <strong className="text-slate-700">{formatCurrency(unitPrice)}</strong>
              {selectedModifiersList.length > 0 && (
                <span className="ml-1 text-[11px]">
                  ({formatCurrency(basePrice)} + {selectedModifiersList.length} tùy chọn)
                </span>
              )}
            </span>
            <span className="text-base font-bold text-blue-600">
              Tổng: {formatCurrency(totalLinePrice)}
            </span>
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-slate-200 bg-slate-50 flex justify-end gap-2">
          <button
            type="button"
            className="px-4 py-2 text-xs font-medium text-slate-700 rounded-lg border border-slate-300 hover:bg-white transition-colors cursor-pointer flex items-center gap-1.5"
            onClick={onClose}
          >
            <span>Hủy</span>
            <kbd className="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-mono bg-slate-200/70 text-slate-600 rounded border border-slate-300">
              Esc
            </kbd>
          </button>
          <button
            type="button"
            className="inline-flex items-center gap-1.5 px-5 py-2 text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm transition-colors cursor-pointer"
            onClick={handleAdd}
          >
            <Plus size={14} />
            <span>Thêm vào giỏ - {formatCurrency(totalLinePrice)}</span>
            <kbd className="hidden sm:inline-block ml-1 px-1.5 py-0.5 text-[10px] font-mono bg-blue-700/70 text-blue-100 rounded border border-blue-400/40">
              ↵ Enter
            </kbd>
          </button>
        </div>
      </div>
    </div>
  );
}

export default POSItemModal;
