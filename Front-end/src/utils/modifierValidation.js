/**
 * Utility functions for validating modifier group selection invariants.
 * Conforms to ADR 0012: Client POS Invariants & Lifecycle Hardening.
 */

/**
 * Determines whether a modifier group is mandatory (requires minSelections >= 1).
 * @param {object} group - The modifier group
 * @returns {boolean}
 */
export function isModifierGroupMandatory(group) {
  return Boolean(group && Number(group.minSelections) > 0);
}

/**
 * Returns the count of selected modifiers for a specific group.
 * Supports Set, Array, or undefined.
 * @param {object} selectedModifiersMap - Map of groupId -> Set or Array
 * @param {string} groupId - The modifier group ID
 * @returns {number}
 */
export function getModifierGroupSelectionCount(selectedModifiersMap, groupId) {
  if (!selectedModifiersMap || !groupId) return 0;
  const selections = selectedModifiersMap[groupId];
  if (!selections) return 0;
  if (selections instanceof Set) return selections.size;
  if (Array.isArray(selections)) return selections.length;
  if (typeof selections === "object") return Object.keys(selections).length;
  return 0;
}

/**
 * Identifies all mandatory modifier groups that have not met their minimum selection requirements.
 * @param {Array<object>} modifierGroups - List of modifier groups
 * @param {object} selectedModifiersMap - Map of groupId -> Set of selected modifier IDs
 * @returns {Array<object>} List of unmet modifier groups
 */
export function getUnmetModifierGroups(modifierGroups, selectedModifiersMap) {
  if (!Array.isArray(modifierGroups) || modifierGroups.length === 0) {
    return [];
  }

  return modifierGroups.filter((group) => {
    if (!isModifierGroupMandatory(group)) {
      return false;
    }
    const selectedCount = getModifierGroupSelectionCount(
      selectedModifiersMap,
      group.groupId
    );
    const requiredMin = Number(group.minSelections);
    return selectedCount < requiredMin;
  });
}

/**
 * Validates modifier group selections for an item.
 * @param {Array<object>} modifierGroups - List of modifier groups
 * @param {object} selectedModifiersMap - Map of groupId -> Set of selected modifier IDs
 * @returns {{ isValid: boolean, unmetGroups: Array<object>, errorMessage: string | null }}
 */
export function validateModifierSelections(modifierGroups, selectedModifiersMap) {
  const unmetGroups = getUnmetModifierGroups(modifierGroups, selectedModifiersMap);

  if (unmetGroups.length === 0) {
    return {
      isValid: true,
      unmetGroups: [],
      errorMessage: null,
    };
  }

  const firstUnmet = unmetGroups[0];
  const errorMessage = `Vui lòng chọn đủ tùy chọn cho: ${firstUnmet.name}`;

  return {
    isValid: false,
    unmetGroups,
    errorMessage,
  };
}

/**
 * Generates badge text for mandatory groups in POS item modals.
 * Returns null if the group is optional.
 * @param {object} group - The modifier group
 * @returns {string | null}
 */
export function getModifierGroupBadgeText(group) {
  if (!isModifierGroupMandatory(group)) {
    return null;
  }
  const min = Number(group.minSelections);
  return `Bắt buộc (Chọn tối thiểu ${min})`;
}
