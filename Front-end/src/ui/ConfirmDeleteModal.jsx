import { createPortal } from "react-dom";
import { AlertTriangle, Loader2, Trash2, X } from "lucide-react";
import { useLockBodyScroll } from "../hooks/useLockBodyScroll";
import { useEscapeKey } from "../hooks/useEscapeKey";

/**
 * Reusable Confirmation Modal for Destructive (Delete) Actions.
 *
 * Replaces browser confirmation dialogs across the entire application with an accessible,
 * styled dialog conforming to project design tokens.
 */
function ConfirmDeleteModal({
  isOpen,
  onClose,
  onConfirm,
  title = "Xác nhận xóa",
  entityName = "",
  message,
  isLoading = false,
  confirmText = "Xóa",
  cancelText = "Hủy",
}) {
  useLockBodyScroll(isOpen);
  useEscapeKey(onClose, isOpen && !isLoading);

  if (!isOpen) return null;

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget && !isLoading) {
      onClose();
    }
  };

  const resolvedMessage =
    message !== undefined
      ? message
      : "Bạn có chắc muốn xóa mục này không? Thao tác này không thể hoàn tác.";

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs transition-opacity"
      onClick={handleBackdropClick}
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-delete-title"
      aria-describedby="confirm-delete-message"
    >
      {/* Modal Dialog Card */}
      <div
        className="relative w-full max-w-md bg-white rounded-xl shadow-2xl border border-slate-200 p-6 z-10 transition-all"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top-Right Dismiss Button */}
        <button
          type="button"
          onClick={onClose}
          disabled={isLoading}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
          aria-label="Đóng hộp thoại"
        >
          <X size={18} />
        </button>

        <div className="flex items-start gap-4">
          {/* Warning Icon Badge */}
          <div className="w-12 h-12 rounded-xl bg-red-50 border border-red-100 text-red-600 flex items-center justify-center shrink-0">
            <AlertTriangle size={24} className="text-red-600" />
          </div>

          {/* Text Content */}
          <div className="flex-1 min-w-0">
            <h3
              id="confirm-delete-title"
              className="text-lg font-bold text-slate-900 leading-tight"
            >
              {title}
            </h3>

            <div id="confirm-delete-message" className="mt-2 text-sm text-slate-600 leading-relaxed">
              {entityName ? (
                <p>
                  Bạn có chắc muốn xóa{" "}
                  <span className="font-semibold text-slate-900 break-words">
                    &ldquo;{entityName}&rdquo;
                  </span>
                  không?
                </p>
              ) : null}
              {resolvedMessage && (
                <p className={entityName ? "mt-1 text-slate-500 text-xs" : ""}>
                  {resolvedMessage}
                </p>
              )}
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50 hover:text-slate-900 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
          >
            {cancelText}
          </button>

          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className="inline-flex items-center justify-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer min-w-[5.5rem]"
          >
            {isLoading ? (
              <>
                <Loader2 size={16} className="animate-spin text-white" />
                <span>Đang xóa...</span>
              </>
            ) : (
              <>
                <Trash2 size={15} />
                <span>{confirmText}</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}

export default ConfirmDeleteModal;
