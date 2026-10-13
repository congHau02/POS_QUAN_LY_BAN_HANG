import { useEffect, useRef } from "react";

export function useOutsideClick(handler, listenCapturing = true, refs = null) {
  const defaultRef = useRef();
  const handlerRef = useRef(handler);

  useEffect(() => {
    handlerRef.current = handler;
  });

  useEffect(
    function () {
      function handleClick(e) {
        if (refs) {
          const refList = Array.isArray(refs) ? refs : [refs];
          const isInside = refList.some(
            (r) => r?.current && r.current.contains(e.target)
          );
          if (!isInside) {
            handlerRef.current?.(e);
          }
        } else if (defaultRef.current) {
          if (!defaultRef.current.contains(e.target)) {
            handlerRef.current?.(e);
          }
        } else {
          handlerRef.current?.(e);
        }
      }

      document.addEventListener("click", handleClick, listenCapturing);

      return () =>
        document.removeEventListener("click", handleClick, listenCapturing);
    },
    [listenCapturing, refs]
  );

  return defaultRef;
}
