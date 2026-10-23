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
 * @param {object} selectedModifiersMap - Map of groupId -> Set of modifier IDs
 * @param {string} groupId - The modifier group ID
 * @returns {number}
 */
export function getModifierGroupSelectionCount(selectedModifiersMap, groupId) {
  if (!selectedModifiersMap || !groupId) return 0;
  const selections = selectedModifiersMap[groupId];
  if (!selections) return 0;
  if (selections instanceof Set) return selections.size;
  if (Array.isArray(selections)) return selections.length;
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
 * Identifies modifier groups that exceed their maximum selection limits.
 * @param {Array<object>} modifierGroups - List of modifier groups
 * @param {object} selectedModifiersMap - Map of groupId -> Set of selected modifier IDs
 * @returns {Array<object>} List of exceeded modifier groups
 */
export function getExceededModifierGroups(modifierGroups, selectedModifiersMap) {
  if (!Array.isArray(modifierGroups) || modifierGroups.length === 0) {
    return [];
  }

  return modifierGroups.filter((group) => {
    if (!group?.maxSelections || group.maxSelections <= 0) {
      return false;
    }
    const selectedCount = getModifierGroupSelectionCount(
      selectedModifiersMap,
      group.groupId
    );
    return selectedCount > Number(group.maxSelections);
  });
}

/**
 * Validates modifier group selections for an item against both minSelections and maxSelections boundaries.
 * @param {Array<object>} modifierGroups - List of modifier groups
 * @param {object} selectedModifiersMap - Map of groupId -> Set of selected modifier IDs
 * @returns {{ isValid: boolean, unmetGroups: Array<object>, errorMessage: string | null }}
 */
export function validateModifierSelections(modifierGroups, selectedModifiersMap) {
  const unmetGroups = getUnmetModifierGroups(modifierGroups, selectedModifiersMap);
  if (unmetGroups.length > 0) {
    const firstUnmet = unmetGroups[0];
    return {
      isValid: false,
      unmetGroups,
      errorMessage: `Vui lòng chọn đủ tùy chọn cho: ${firstUnmet.name}`,
    };
  }

  const exceededGroups = getExceededModifierGroups(modifierGroups, selectedModifiersMap);
  if (exceededGroups.length > 0) {
    const firstExceeded = exceededGroups[0];
    return {
      isValid: false,
      unmetGroups: exceededGroups,
      errorMessage: `Vui lòng chọn tối đa ${firstExceeded.maxSelections} tùy chọn cho: ${firstExceeded.name}`,
    };
  }

  return {
    isValid: true,
    unmetGroups: [],
    errorMessage: null,
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
