import { test } from "node:test";
import assert from "node:assert/strict";
import {
  normalizePhoneNumber,
  buildCustomerOrderPayload,
  shouldTriggerCrmLookup,
  getNextCustomerOnPhoneChange,
  getNextCustomerOnNameChange,
} from "../src/utils/customerSync.js";

test("normalizePhoneNumber trims and removes internal spaces", () => {
  assert.equal(normalizePhoneNumber("  090 123 4567  "), "0901234567");
  assert.equal(normalizePhoneNumber(""), "");
  assert.equal(normalizePhoneNumber(null), "");
});

test("buildCustomerOrderPayload captures customer name and phone when provided", () => {
  const customer = {
    customerId: "cust-123",
    customerName: "Nguyen Van A",
    mobileNumber: "0901234567",
  };

  const payload = buildCustomerOrderPayload(customer);
  assert.equal(payload.customerId, "cust-123");
  assert.equal(payload.customerName, "Nguyen Van A");
  assert.equal(payload.phoneNumber, "0901234567");
});

test("buildCustomerOrderPayload defaults to guest credentials when fields are empty or whitespace", () => {
  const emptyPayload = buildCustomerOrderPayload({});
  assert.equal(emptyPayload.customerId, null);
  assert.equal(emptyPayload.customerName, "Người dùng mặc định");
  assert.equal(emptyPayload.phoneNumber, "0000000000");

  const whitespacePayload = buildCustomerOrderPayload({
    customerName: "   ",
    mobileNumber: "   ",
  });
  assert.equal(whitespacePayload.customerId, null);
  assert.equal(whitespacePayload.customerName, "Người dùng mặc định");
  assert.equal(whitespacePayload.phoneNumber, "0000000000");
});

test("buildCustomerOrderPayload preserves typed name/phone even without registered customerId", () => {
  // Fast checkout scenario: cashier typed phone and name, customer is new / unregistered
  const typedCustomer = {
    customerId: null,
    customerName: "Khách lẻ vãng lai",
    mobileNumber: "0988776655",
  };

  const payload = buildCustomerOrderPayload(typedCustomer);
  assert.equal(payload.customerId, null);
  assert.equal(payload.customerName, "Khách lẻ vãng lai");
  assert.equal(payload.phoneNumber, "0988776655");
});

test("shouldTriggerCrmLookup returns true only for valid unverified numbers with at least 8 digits", () => {
  assert.equal(shouldTriggerCrmLookup("0901234", null), false); // 7 digits
  assert.equal(shouldTriggerCrmLookup("09012345", null), true); // 8 digits
  assert.equal(shouldTriggerCrmLookup("0901234567", null), true); // 10 digits
  assert.equal(shouldTriggerCrmLookup("  0901234567  ", null), true); // trimmed 10 digits

  // Already verified phone number should not re-trigger lookup
  assert.equal(shouldTriggerCrmLookup("0901234567", "0901234567"), false);
  // Changing phone number after verification should re-trigger lookup
  assert.equal(shouldTriggerCrmLookup("0901234568", "0901234567"), true);
});

test("getNextCustomerOnPhoneChange clears customerId and updates mobileNumber immediately", () => {
  const current = {
    customerId: "cust-99",
    customerName: "Tran Van C",
    mobileNumber: "0901234567",
  };

  const next = getNextCustomerOnPhoneChange(current, "0901234568");
  assert.equal(next.customerId, null);
  assert.equal(next.mobileNumber, "0901234568");
  assert.equal(next.customerName, "Tran Van C");
});

test("getNextCustomerOnNameChange updates customerName and clears customerId to prevent identity mismatch", () => {
  const current = {
    customerId: "cust-99",
    customerName: "Tran Van C",
    mobileNumber: "0901234567",
  };

  const next = getNextCustomerOnNameChange(current, "Tran Van D");
  assert.equal(next.customerId, null);
  assert.equal(next.mobileNumber, "0901234567");
  assert.equal(next.customerName, "Tran Van D");
});
