import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC_DIR = path.resolve(__dirname, "../src");

test("CartSummary.jsx does not invoke setState synchronously during render", () => {
  const content = fs.readFileSync(
    path.join(SRC_DIR, "features/Explore/CartSummary.jsx"),
    "utf8"
  );

  // Ensure render pass does not mutate state directly
  assert.equal(
    /if\s*\(\s*promoError\s*&&\s*appliedCoupon\s*\)\s*\{\s*setAppliedCoupon\s*\(/.test(
      content
    ),
    false,
    "CartSummary must not call setAppliedCoupon directly in the render pass"
  );
});

test("CartSummary.jsx consolidates PayOS modal dismissal into handleClosePayOSModal", () => {
  const content = fs.readFileSync(
    path.join(SRC_DIR, "features/Explore/CartSummary.jsx"),
    "utf8"
  );

  assert.equal(
    content.includes("handleClosePayOSModal"),
    true,
    "CartSummary must define handleClosePayOSModal"
  );
  const occurrences = (content.match(/onClick=\{handleClosePayOSModal\}/g) || []).length;
  assert.equal(
    occurrences >= 2,
    true,
    "handleClosePayOSModal must be attached to both backdrop and dismiss button"
  );
});

test("Menubar.jsx consolidates outside click listeners into a single call", () => {
  const content = fs.readFileSync(
    path.join(SRC_DIR, "ui/Menubar.jsx"),
    "utf8"
  );

  const outsideClickMatches = content.match(/useOutsideClick\s*\(/g) || [];
  assert.equal(
    outsideClickMatches.length,
    1,
    "Menubar must register exactly one useOutsideClick instance"
  );
});

test("POS modals are dynamically imported via React.lazy across explore and payment views", () => {
  const filesToVerify = [
    {
      file: "features/Explore/DisplayItem.jsx",
      modal: "POSItemModal",
    },
    {
      file: "features/Explore/CartSummary.jsx",
      modal: "ReceiptPopup",
    },
    {
      file: "features/Payment/CompletedPayment.jsx",
      modal: "ReceiptPopup",
    },
    {
      file: "pages/OrderHistory.jsx",
      modal: "ReceiptPopup",
    },
  ];

  for (const { file, modal } of filesToVerify) {
    const content = fs.readFileSync(path.join(SRC_DIR, file), "utf8");

    // Must use lazy import
    const lazyRegex = new RegExp(`lazy\\(\\s*\\(\\)\\s*=>\\s*import\\(["'].*${modal}`);
    assert.ok(
      lazyRegex.test(content),
      `${file} must import ${modal} using React.lazy`
    );

    // Must have preload handler
    const preloadRegex = new RegExp(`preload${modal}\\s*=\\s*\\(\\)\\s*=>\\s*import`);
    assert.ok(
      preloadRegex.test(content),
      `${file} must declare preload${modal} function`
    );
  }
});
