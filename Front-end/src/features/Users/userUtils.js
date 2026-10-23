/**
 * Determines if a target user account corresponds to the currently authenticated session user.
 *
 * @param {Object} targetUser - The user object to evaluate
 * @param {Object} currentUser - The currently authenticated session user
 * @returns {boolean} True if emails match, false otherwise
 */
export function isSelfAccount(targetUser, currentUser) {
  if (!targetUser?.email || !currentUser?.email) return false;
  return targetUser.email.trim().toLowerCase() === currentUser.email.trim().toLowerCase();
}
