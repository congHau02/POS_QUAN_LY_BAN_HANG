import { useState, useCallback } from "react";

/**
 * Computes a deterministic unique identifier for a cart line item
 * incorporating the item ID, variant ID, and sorted modifier selections.
 *
 * @param {Object} item
 * @returns {string}
 */
export function computeCartItemId(item) {
  if (item.cartItemId) return item.cartItemId;
  const modifierKey = (item.selectedModifiers || [])
    .map((m) => m.modifierId)
    .sort()
    .join("-");
  return `${item.itemId}_${item.variantId || "default"}_${modifierKey}`;
}

/**
 * Pure state reducer function that appends or increments an item in the cart.
 *
 * @param {Array} prevItems
 * @param {Object} item
 * @returns {Array}
 */
export function addItemToCart(prevItems, item) {
  const cartItemId = computeCartItemId(item);
  const itemToAdd = {
    ...item,
    cartItemId,
    quantity: item.quantity || 1,
  };

  const existingIndex = prevItems.findIndex((ci) => ci.cartItemId === cartItemId);

  if (existingIndex > -1) {
    return prevItems.map((ci, idx) =>
      idx === existingIndex
        ? { ...ci, quantity: ci.quantity + itemToAdd.quantity }
        : ci
    );
  }

  return [...prevItems, itemToAdd];
}

/**
 * Pure state reducer function that removes an item from the cart.
 *
 * @param {Array} prevItems
 * @param {string} cartItemId
 * @returns {Array}
 */
export function removeCartItem(prevItems, cartItemId) {
  return prevItems.filter(
    (cartItem) => cartItem.cartItemId !== cartItemId && cartItem.itemId !== cartItemId
  );
}

/**
 * Pure state reducer function that updates the quantity of an item,
 * removing it if the quantity drops to zero or below.
 *
 * @param {Array} prevItems
 * @param {string} cartItemId
 * @param {number} newQuantity
 * @returns {Array}
 */
export function updateCartItemQuantity(prevItems, cartItemId, newQuantity) {
  if (newQuantity <= 0) {
    return removeCartItem(prevItems, cartItemId);
  }

  return prevItems.map((cartItem) =>
    cartItem.cartItemId === cartItemId || cartItem.itemId === cartItemId
      ? { ...cartItem, quantity: newQuantity }
      : cartItem
  );
}

/**
 * Pure function returning an empty cart.
 *
 * @returns {Array}
 */
export function clearCartItems() {
  return [];
}

export function useCartItem() {
  const [cartItems, setCartItems] = useState([]);

  const addToCart = useCallback((item) => {
    setCartItems((prevItems) => addItemToCart(prevItems, item));
  }, []);

  const removeFromCart = useCallback((cartItemId) => {
    setCartItems((prevItems) => removeCartItem(prevItems, cartItemId));
  }, []);

  const updateQuantity = useCallback((cartItemId, newQuantity) => {
    setCartItems((prevItems) => updateCartItemQuantity(prevItems, cartItemId, newQuantity));
  }, []);

  const clearCart = useCallback(() => {
    setCartItems(clearCartItems());
  }, []);

  return { cartItems, addToCart, removeFromCart, updateQuantity, clearCart };
}

export default useCartItem;
