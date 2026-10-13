import { useEffect, useRef } from "react";

export function useOutsideClick(handler, listenCapturing = true, refs = null) {
  const defaultRef = useRef();
  const handlerRef = useRef(handler);
  const refsRef = useRef(refs);

  useEffect(() => {
    handlerRef.current = handler;
  });

  useEffect(() => {
    refsRef.current = refs;
  });

  useEffect(
    function () {
      function handleClick(e) {
        const currentRefs = refsRef.current;
        if (currentRefs) {
          const refList = Array.isArray(currentRefs) ? currentRefs : [currentRefs];
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
    [listenCapturing]
  );

  return defaultRef;
}
