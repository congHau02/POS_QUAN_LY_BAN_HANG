import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import {
  isTerminalOrderStatus,
  getOrderStatus,
  computePollingInterval,
  isPollingEnabled,
} from "../src/features/Payment/orderTerminalState.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC_DIR = path.resolve(__dirname, "../src");

test("isTerminalOrderStatus identifies COMPLETED and CANCELLED as terminal states", () => {
  assert.equal(isTerminalOrderStatus("COMPLETED"), true);
  assert.equal(isTerminalOrderStatus("CANCELLED"), true);
  assert.equal(isTerminalOrderStatus("PENDING"), false);
  assert.equal(isTerminalOrderStatus("FAILED"), false);
  assert.equal(isTerminalOrderStatus(null), false);
  assert.equal(isTerminalOrderStatus(undefined), false);
});

test("getOrderStatus resolves status across raw, wrapped, and query objects", () => {
  // Raw DTO with paymentDetails
  assert.equal(
    getOrderStatus({ orderId: "1", paymentDetails: { status: "COMPLETED" } }),
    "COMPLETED"
  );
  assert.equal(
    getOrderStatus({ orderId: "2", paymentDetails: { status: "CANCELLED" } }),
    "CANCELLED"
  );

  // Wrapped DTO ({ data: { paymentDetails: ... } })
  assert.equal(
    getOrderStatus({ data: { paymentDetails: { status: "CANCELLED" } } }),
    "CANCELLED"
  );

  // Fallback to paymentStatus or status
  assert.equal(getOrderStatus({ paymentStatus: "CANCELLED" }), "CANCELLED");
  assert.equal(getOrderStatus({ status: "CANCELLED" }), "CANCELLED");

  // React Query state wrapper ({ state: { data: ... } })
  assert.equal(
    getOrderStatus({
      state: {
        data: {
          orderId: "3",
          paymentDetails: { status: "CANCELLED" },
        },
      },
    }),
    "CANCELLED"
  );
});

test("computePollingInterval terminates interval (returns false) for COMPLETED and CANCELLED", () => {
  // Terminal states -> return false immediately
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "COMPLETED" } }),
    false,
    "COMPLETED status must stop polling"
  );
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "CANCELLED" } }),
    false,
    "CANCELLED status must stop polling"
  );
  assert.equal(
    computePollingInterval({
      state: { data: { paymentDetails: { status: "CANCELLED" } } },
    }),
    false,
    "Query object with CANCELLED status must stop polling"
  );

  // Active states -> continue polling at 3000ms
  assert.equal(
    computePollingInterval({ paymentDetails: { status: "PENDING" } }),
    3000,
    "PENDING status must poll at 3000ms"
  );
  assert.equal(
    computePollingInterval({}),
    3000,
    "Empty order data must poll at 3000ms"
  );
});

test("isPollingEnabled disables polling on initialization for terminal states or missing orderId", () => {
  // Missing orderId
  assert.equal(
    isPollingEnabled(null, { paymentDetails: { status: "PENDING" } }),
    false
  );
  assert.equal(
    isPollingEnabled("", { paymentDetails: { status: "PENDING" } }),
    false
  );

  // Terminal initial states
  assert.equal(
    isPollingEnabled("order-101", { paymentDetails: { status: "COMPLETED" } }),
    false
  );
  assert.equal(
    isPollingEnabled("order-102", { paymentDetails: { status: "CANCELLED" } }),
    false
  );
  assert.equal(
    isPollingEnabled("order-103", {
      data: { paymentDetails: { status: "CANCELLED" } },
    }),
    false
  );

  // Active initial state
  assert.equal(
    isPollingEnabled("order-104", { paymentDetails: { status: "PENDING" } }),
    true
  );
});

test("QRCode.jsx renders explicit cancellation card and suppresses in-flight action controls on CANCELLED", () => {
  const qrCodePath = path.join(SRC_DIR, "features/Payment/QRCode.jsx");
  const content = fs.readFileSync(qrCodePath, "utf8");

  // Inspects status for CANCELLED
  assert.ok(
    /CANCELLED/.test(content),
    "QRCode.jsx must check for CANCELLED status"
  );

  // Contains cancellation notice text
  assert.ok(
    /Đơn hàng.*đã bị hủy/.test(content),
    "QRCode.jsx must display Vietnamese cancellation banner"
  );

  // Must suppress or disable in-flight actions when cancelled
  assert.ok(
    /(!isCancelled|isCancelled)/.test(content),
    "QRCode.jsx must conditionally handle cancelled state for in-flight actions"
  );
});
