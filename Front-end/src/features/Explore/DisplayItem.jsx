import { useState, memo, lazy, Suspense } from "react";
import { formatCurrency } from "../../utils/formatCurrency";
import { Plus } from "lucide-react";

const POSItemModal = lazy(() => import("./POSItemModal"));
const preloadPOSItemModal = () => import("./POSItemModal");

function DisplayItem({ addToCart, item }) {
  const [isModalOpen, setIsModalOpen] = useState(false);

  const hasVariants = item.variants && item.variants.length > 0;
  const hasModifiers = item.modifierGroups && item.modifierGroups.length > 0;
  const needsCustomization = hasVariants || hasModifiers;

  const handleCardClick = () => {
    if (needsCustomization) {
      setIsModalOpen(true);
    } else {
      addToCart({
        cartItemId: `${item.itemId}_default`,
        name: item.name,
        basePrice: item.price,
        price: item.price,
        quantity: 1,
        itemId: item.itemId,
        variantId: item.variants?.[0]?.variantId || null,
        selectedModifiers: [],
      });
    }
  };

  return (
    <>
      <div
        className="p-3 bg-white rounded-xl border border-slate-200 shadow-xs hover:shadow-md hover:border-blue-400 transition-all cursor-pointer flex items-center justify-between gap-3 group"
        onClick={handleCardClick}
        onMouseEnter={() => {
          if (needsCustomization) {
            preloadPOSItemModal();
          }
        }}
      >
        <div className="flex items-center gap-3 min-w-0">
          <img
            src={item.imgUrl || "https://placehold.co/60x60?text=MH"}
            alt={item.name}
            loading="lazy"
            decoding="async"
            width={48}
            height={48}
            className="w-12 h-12 rounded-lg object-cover border border-slate-200 bg-slate-50 flex-shrink-0"
          />
          <div className="min-w-0">
            <h6 className="text-xs font-semibold text-slate-900 truncate group-hover:text-blue-600 transition-colors">
              {item.name}
            </h6>
            <p className="text-xs font-bold text-slate-800 mt-0.5">
              {formatCurrency(item.price)}
            </p>
            <div className="flex items-center gap-1 mt-1 flex-wrap">
              {hasVariants && (
                <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-medium bg-slate-100 text-slate-600">
                  {item.variants.length} var
                </span>
              )}
              {hasModifiers && (
                <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-medium bg-blue-50 text-blue-700">
                  Custom
                </span>
              )}
            </div>
          </div>
        </div>

        <button
          type="button"
          className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 group-hover:bg-blue-600 group-hover:text-white flex items-center justify-center transition-colors flex-shrink-0 cursor-pointer"
          onClick={(e) => {
            e.stopPropagation();
            handleCardClick();
          }}
          title={needsCustomization ? "Tùy chỉnh mặt hàng" : "Thêm vào giỏ hàng"}
        >
          <Plus size={16} />
        </button>
      </div>

      <Suspense fallback={null}>
        {isModalOpen && (
          <POSItemModal
            item={item}
            isOpen={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            onAddToCart={addToCart}
          />
        )}
      </Suspense>
    </>
  );
}

export default memo(DisplayItem);
