import assert from "node:assert/strict";
import test from "node:test";
import {
  computeCartItemId,
  addItemToCart,
  updateCartItemQuantity,
  removeCartItem,
  clearCartItems,
} from "../src/features/Explore/useCartItem.js";

test("computeCartItemId generates deterministic id for item with no variant or modifiers", () => {
  const item = { itemId: "item-1" };
  const id = computeCartItemId(item);
  assert.equal(id, "item-1_default_");
});

test("computeCartItemId incorporates variantId when present", () => {
  const item = { itemId: "item-1", variantId: "var-large" };
  const id = computeCartItemId(item);
  assert.equal(id, "item-1_var-large_");
});

test("computeCartItemId generates sorted deterministic modifier key regardless of selection order", () => {
  const itemA = {
    itemId: "item-1",
    variantId: "var-1",
    selectedModifiers: [{ modifierId: "mod-b" }, { modifierId: "mod-a" }],
  };
  const itemB = {
    itemId: "item-1",
    variantId: "var-1",
    selectedModifiers: [{ modifierId: "mod-a" }, { modifierId: "mod-b" }],
  };

  const idA = computeCartItemId(itemA);
  const idB = computeCartItemId(itemB);

  assert.equal(idA, "item-1_var-1_mod-a-mod-b");
  assert.equal(idB, "item-1_var-1_mod-a-mod-b");
  assert.equal(idA, idB);
});

test("computeCartItemId preserves custom cartItemId when already present", () => {
  const item = { itemId: "item-1", cartItemId: "custom-cart-id" };
  assert.equal(computeCartItemId(item), "custom-cart-id");
});

test("addItemToCart adds new item with default quantity 1", () => {
  const cart = [];
  const item = { itemId: "item-1", name: "Trà Đào", price: 30000 };
  const result = addItemToCart(cart, item);

  assert.equal(result.length, 1);
  assert.equal(result[0].itemId, "item-1");
  assert.equal(result[0].quantity, 1);
  assert.equal(result[0].cartItemId, "item-1_default_");
});

test("addItemToCart increments quantity for duplicate item with identical modifier configuration", () => {
  const initialCart = [
    {
      itemId: "item-1",
      cartItemId: "item-1_default_",
      name: "Trà Đào",
      price: 30000,
      quantity: 1,
    },
  ];

  const result = addItemToCart(initialCart, {
    itemId: "item-1",
    quantity: 2,
  });

  assert.equal(result.length, 1);
  assert.equal(result[0].quantity, 3);
});

test("addItemToCart creates separate line items for same item with different modifier selections", () => {
  const initialCart = [
    {
      itemId: "item-1",
      cartItemId: "item-1_default_mod-sugar50",
      name: "Trà Đào (50% đường)",
      quantity: 1,
    },
  ];

  const itemDifferentMod = {
    itemId: "item-1",
    name: "Trà Đào (100% đường)",
    selectedModifiers: [{ modifierId: "mod-sugar100" }],
    quantity: 1,
  };

  const result = addItemToCart(initialCart, itemDifferentMod);

  assert.equal(result.length, 2);
  assert.equal(result[0].cartItemId, "item-1_default_mod-sugar50");
  assert.equal(result[1].cartItemId, "item-1_default_mod-sugar100");
});

test("updateCartItemQuantity updates quantity when newQuantity is positive", () => {
  const cart = [
    { cartItemId: "item-1_default_", quantity: 2 },
    { cartItemId: "item-2_default_", quantity: 1 },
  ];

  const result = updateCartItemQuantity(cart, "item-1_default_", 5);

  assert.equal(result.length, 2);
  assert.equal(result[0].quantity, 5);
  assert.equal(result[1].quantity, 1);
});

test("updateCartItemQuantity removes item when newQuantity is 0 or negative", () => {
  const cart = [
    { cartItemId: "item-1_default_", quantity: 2 },
    { cartItemId: "item-2_default_", quantity: 1 },
  ];

  const resultZero = updateCartItemQuantity(cart, "item-1_default_", 0);
  assert.equal(resultZero.length, 1);
  assert.equal(resultZero[0].cartItemId, "item-2_default_");

  const resultNegative = updateCartItemQuantity(cart, "item-2_default_", -1);
  assert.equal(resultNegative.length, 1);
  assert.equal(resultNegative[0].cartItemId, "item-1_default_");
});

test("removeCartItem removes item matching cartItemId or itemId", () => {
  const cart = [
    { cartItemId: "cart-item-1", itemId: "item-1", quantity: 1 },
    { cartItemId: "cart-item-2", itemId: "item-2", quantity: 2 },
  ];

  const result = removeCartItem(cart, "cart-item-1");
  assert.equal(result.length, 1);
  assert.equal(result[0].cartItemId, "cart-item-2");

  const resultFallback = removeCartItem(cart, "item-2");
  assert.equal(resultFallback.length, 1);
  assert.equal(resultFallback[0].cartItemId, "cart-item-1");
});

test("clearCartItems returns empty array", () => {
  assert.deepEqual(clearCartItems(), []);
});
