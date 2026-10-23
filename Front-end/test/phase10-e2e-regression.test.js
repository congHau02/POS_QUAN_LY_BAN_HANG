import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  computeDatePresetRange,
  formatLocalDate,
  formatLocalDateTime,
} from "../src/utils/activityLogDates.js";
import {
  isTerminalOrderStatus,
  computePollingInterval,
  isPollingEnabled,
} from "../src/features/Payment/orderTerminalState.js";
import {
  buildCustomerOrderPayload,
  getNextCustomerOnPhoneChange,
  getNextCustomerOnNameChange,
} from "../src/utils/customerSync.js";
import {
  validateModifierSelections,
  getModifierGroupBadgeText,
  isModifierGroupMandatory,
} from "../src/utils/modifierValidation.js";
import { isSelfAccount } from "../src/features/Users/userUtils.js";
import {
  computeCartItemId,
  addItemToCart,
  updateCartItemQuantity,
} from "../src/features/Explore/useCartItem.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("Phase 10 Regression Suite - Finding 1: Local calendar date filtering prevents early morning timezone drift", () => {
  // Test at 01:00 AM local time in UTC+7 (18:00 UTC previous day)
  const earlyMorning = new Date(2026, 9, 23, 1, 0, 0);
  assert.equal(formatLocalDate(earlyMorning), "2026-10-23");

  const range = computeDatePresetRange("today", earlyMorning);
  assert.equal(range.startDate, "2026-10-23");
  assert.equal(range.endDate, "2026-10-23");

  const sevenDays = computeDatePresetRange("7days", earlyMorning);
  assert.equal(sevenDays.startDate, "2026-10-16");
  assert.equal(sevenDays.endDate, "2026-10-23");

  const thirtyDays = computeDatePresetRange("30days", earlyMorning);
  assert.equal(thirtyDays.startDate, "2026-09-23");
  assert.equal(thirtyDays.endDate, "2026-10-23");
});

test("Phase 10 Regression Suite - Finding 2: Timestamp formatting conforms to Vietnamese locale", () => {
  const sampleDate = new Date(2026, 9, 23, 8, 30, 0);
  const formatted = formatLocalDateTime(sampleDate);
  assert.match(formatted, /23\/10\/2026/);
  assert.match(formatted, /08:30/);
});

test("Phase 10 Regression Suite - Findings 3, 4, 5: Polling terminates and QR cancels on CANCELLED order", () => {
  // Terminal state detection
  assert.equal(isTerminalOrderStatus("CANCELLED"), true);
  assert.equal(isTerminalOrderStatus("COMPLETED"), true);
  assert.equal(isTerminalOrderStatus("PENDING"), false);

  // Polling interval stops
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "CANCELLED" } }),
    false
  );
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "COMPLETED" } }),
    false
  );
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "PENDING" } }),
    3000
  );

  // Initial polling enabled condition
  assert.equal(
    isPollingEnabled("order-1", { paymentDetails: { status: "CANCELLED" } }),
    false
  );

  // QRCode.jsx UI checks
  const qrCodeCode = fs.readFileSync(
    path.join(srcDir, "features", "Payment", "QRCode.jsx"),
    "utf-8"
  );
  assert.match(qrCodeCode, /CANCELLED/);
  assert.match(qrCodeCode, /Đơn hàng.*đã bị hủy/);
});

test("Phase 10 Regression Suite - Findings 6, 7: Fast checkout captures customer phone/name without blur", () => {
  let customer = { customerId: null, customerName: "", mobileNumber: "" };

  // Keystrokes update customer immediately
  customer = getNextCustomerOnPhoneChange(customer, "0912345678");
  customer = getNextCustomerOnNameChange(customer, "Nguyen Van FastCheckout");

  // Direct checkout payload build
  const payload = buildCustomerOrderPayload(customer);
  assert.equal(payload.phoneNumber, "0912345678");
  assert.equal(payload.customerName, "Nguyen Van FastCheckout");
  assert.equal(payload.customerId, null);
});

test("Phase 10 Regression Suite - Findings 8, 9: Mandatory modifier enforcement and visual badge indicators", () => {
  const groups = [
    { groupId: "size", name: "Kích cỡ", minSelections: 1, maxSelections: 1 },
    { groupId: "ice", name: "Đá", minSelections: 0, maxSelections: 1 },
  ];

  // Group requires selection badge
  assert.equal(isModifierGroupMandatory(groups[0]), true);
  assert.equal(isModifierGroupMandatory(groups[1]), false);
  assert.equal(
    getModifierGroupBadgeText(groups[0]),
    "Bắt buộc (Chọn tối thiểu 1)"
  );
  assert.equal(getModifierGroupBadgeText(groups[1]), null);

  // Validation blocks missing mandatory selection
  const invalidResult = validateModifierSelections(groups, {});
  assert.equal(invalidResult.isValid, false);
  assert.match(invalidResult.errorMessage, /Kích cỡ/);

  // Validation succeeds when mandatory selection provided
  const validResult = validateModifierSelections(groups, {
    size: new Set(["size-L"]),
  });
  assert.equal(validResult.isValid, true);
  assert.equal(validResult.errorMessage, null);
});

test("Phase 10 Regression Suite - Findings 10, 11: Admin self-deletion safeguard prevents active user deletion", () => {
  const activeAdmin = { email: "admin@store.vn", name: "Head Admin" };
  const targetAdmin = { email: "ADMIN@store.vn", name: "Head Admin" };
  const otherStaff = { email: "staff@store.vn", name: "Staff Member" };

  assert.equal(isSelfAccount(targetAdmin, activeAdmin), true);
  assert.equal(isSelfAccount(otherStaff, activeAdmin), false);

  // Verify UserItem.jsx disables delete button for self
  const userItemCode = fs.readFileSync(
    path.join(srcDir, "features", "Users", "UserItem.jsx"),
    "utf-8"
  );
  assert.match(userItemCode, /isSelfAccount/);
  assert.match(userItemCode, /Không thể tự xóa tài khoản đang đăng nhập/);

  // Verify UsersList.jsx blocks delete action for self
  const usersListCode = fs.readFileSync(
    path.join(srcDir, "features", "Users", "UsersList.jsx"),
    "utf-8"
  );
  assert.match(usersListCode, /isSelfAccount/);
});

test("Phase 10 Regression Suite - Finding 12: Dead code files are pruned and unreferenced", () => {
  const deadFiles = [
    path.join(srcDir, "ui", "Modal.jsx"),
    path.join(srcDir, "features", "Modifiers", "useUpdateModifierGroup.js"),
    path.join(srcDir, "pages", "Explore", "Explore.css"),
    path.join(srcDir, "App.css"),
  ];

  for (const file of deadFiles) {
    assert.equal(
      fs.existsSync(file),
      false,
      `File ${file} should have been pruned`
    );
  }

  const appJsx = fs.readFileSync(path.join(srcDir, "App.jsx"), "utf-8");
  assert.equal(appJsx.includes("App.css"), false);
});

test("Phase 10 Regression Suite - Finding 13: ConfirmDeleteModal reuses shared hooks", () => {
  const code = fs.readFileSync(
    path.join(srcDir, "ui", "ConfirmDeleteModal.jsx"),
    "utf-8"
  );
  assert.match(code, /useLockBodyScroll/);
  assert.match(code, /useEscapeKey/);
});

test("Phase 10 Regression Suite - Finding 14: Object URLs are revoked on dialog cleanup", () => {
  const catModalCode = fs.readFileSync(
    path.join(srcDir, "features", "Category", "EditCategoryModal.jsx"),
    "utf-8"
  );
  const itemModalCode = fs.readFileSync(
    path.join(srcDir, "features", "Items", "EditItemModal.jsx"),
    "utf-8"
  );

  assert.match(catModalCode, /URL\.revokeObjectURL/);
  assert.match(itemModalCode, /URL\.revokeObjectURL/);
});

test("Phase 10 Regression Suite - Finding 15: Services unwrap response.data and UI spelling is correct", () => {
  const catService = fs.readFileSync(
    path.join(srcDir, "services", "CategoryService.js"),
    "utf-8"
  );
  const itemService = fs.readFileSync(
    path.join(srcDir, "services", "ItemService.js"),
    "utf-8"
  );
  const userService = fs.readFileSync(
    path.join(srcDir, "services", "UserService.js"),
    "utf-8"
  );

  assert.match(catService, /return\s+response\.data/);
  assert.match(itemService, /return\s+response\.data/);
  assert.match(userService, /return\s+response\.data/);

  // Spelling check in EditCategoryModal
  const editCatCode = fs.readFileSync(
    path.join(srcDir, "features", "Category", "EditCategoryModal.jsx"),
    "utf-8"
  );
  assert.match(editCatCode, /Chỉnh sửa danh mục/);
  assert.match(editCatCode, /Nhập mô tả danh mục/);
  assert.equal(editCatCode.includes("Chỉnh sủa"), false);
  assert.equal(editCatCode.includes("Nhập mõ"), false);
});

test("Phase 10 Regression Suite - Shopping cart items compute deterministic signatures and quantity adjustments", () => {
  const itemA = {
    itemId: "it-1",
    variantId: "v-1",
    selectedModifiers: [{ modifierId: "m-b" }, { modifierId: "m-a" }],
  };
  const itemB = {
    itemId: "it-1",
    variantId: "v-1",
    selectedModifiers: [{ modifierId: "m-a" }, { modifierId: "m-b" }],
  };

  assert.equal(computeCartItemId(itemA), computeCartItemId(itemB));

  let cart = addItemToCart([], itemA);
  assert.equal(cart.length, 1);
  assert.equal(cart[0].quantity, 1);

  cart = addItemToCart(cart, itemB);
  assert.equal(cart.length, 1);
  assert.equal(cart[0].quantity, 2);

  cart = updateCartItemQuantity(cart, cart[0].cartItemId, 0);
  assert.equal(cart.length, 0);
});
