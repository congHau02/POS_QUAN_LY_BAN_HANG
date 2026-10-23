/**
 * Utility functions for customer synchronization and checkout payload preparation.
 * Conforms to ADR 0012: Client POS Invariants & Lifecycle Hardening.
 */

/**
 * Builds customer fields for the create order request payload.
 * Fallbacks to default guest credentials when fields are empty or whitespace.
 * @param {object} customer - Customer object { customerId, customerName, mobileNumber }
 * @returns {{ customerId: string | null, customerName: string, phoneNumber: string }}
 */
export function buildCustomerOrderPayload(customer) {
  const customerId = customer?.customerId || null;
  const rawName = typeof customer?.customerName === "string" ? customer.customerName.trim() : "";
  const rawPhone = typeof customer?.mobileNumber === "string" ? customer.mobileNumber.trim() : "";

  return {
    customerId,
    customerName: rawName || "Người dùng mặc định",
    phoneNumber: rawPhone || "0000000000",
  };
}

/**
 * Checks whether an automatic background CRM lookup should trigger for a phone number.
 * Triggers when phone has at least 8 digits and hasn't already been verified.
 * @param {string} phoneNumber - The phone number entered
 * @param {string | null} verifiedCustomerPhone - The phone number of the currently verified CRM customer
 * @returns {boolean}
 */
export function shouldTriggerCrmLookup(phoneNumber, verifiedCustomerPhone) {
  if (!phoneNumber) return false;
  const digits = phoneNumber.trim().replace(/\s+/g, "");
  if (digits.length < 8) return false;
  if (verifiedCustomerPhone && verifiedCustomerPhone === digits) return false;
  return true;
}

/**
 * Computes next customer state when phone number changes during fast POS entry.
 * Clears customerId because changing phone invalidates existing verification.
 * @param {object} currentCustomer - Current customer state
 * @param {string} nextPhone - New phone number input value
 * @returns {object} Next customer state
 */
export function getNextCustomerOnPhoneChange(currentCustomer, nextPhone) {
  return {
    ...currentCustomer,
    customerId: null,
    mobileNumber: nextPhone,
  };
}

/**
 * Computes next customer state when customer name changes during fast POS entry.
 * @param {object} currentCustomer - Current customer state
 * @param {string} nextName - New customer name input value
 * @returns {object} Next customer state
 */
export function getNextCustomerOnNameChange(currentCustomer, nextName) {
  return {
    ...currentCustomer,
    customerName: nextName,
  };
}
