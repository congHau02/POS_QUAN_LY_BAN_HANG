import { test } from "node:test";
import assert from "node:assert/strict";
import {
  isModifierGroupMandatory,
  getModifierGroupSelectionCount,
  getUnmetModifierGroups,
  getExceededModifierGroups,
  validateModifierSelections,
  getModifierGroupBadgeText,
  getDefaultModifiersMap,
} from "../src/utils/modifierValidation.js";

test("isModifierGroupMandatory identifies groups requiring at least 1 selection", () => {
  assert.equal(isModifierGroupMandatory({ minSelections: 1 }), true);
  assert.equal(isModifierGroupMandatory({ minSelections: 2 }), true);
  assert.equal(isModifierGroupMandatory({ minSelections: 0 }), false);
  assert.equal(isModifierGroupMandatory({ minSelections: null }), false);
  assert.equal(isModifierGroupMandatory({}), false);
  assert.equal(isModifierGroupMandatory(null), false);
});

test("getModifierGroupSelectionCount correctly counts Set, Array, or missing selections", () => {
  const map = {
    group1: new Set(["mod-1", "mod-2"]),
    group2: ["mod-3"],
    group3: new Set(),
  };

  assert.equal(getModifierGroupSelectionCount(map, "group1"), 2);
  assert.equal(getModifierGroupSelectionCount(map, "group2"), 1);
  assert.equal(getModifierGroupSelectionCount(map, "group3"), 0);
  assert.equal(getModifierGroupSelectionCount(map, "non-existent"), 0);
  assert.equal(getModifierGroupSelectionCount(null, "group1"), 0);
});

test("getUnmetModifierGroups returns empty array when all required groups are satisfied", () => {
  const groups = [
    { groupId: "g-size", name: "Kích cỡ", minSelections: 1 },
    { groupId: "g-opt", name: "Topping", minSelections: 0 },
  ];
  const unmet = getUnmetModifierGroups(groups, {
    "g-size": new Set(["m-1"]),
  });
  assert.deepEqual(unmet, []);
});

test("getExceededModifierGroups identifies groups with selections exceeding maxSelections", () => {
  const groups = [
    { groupId: "g-single", name: "Kích cỡ", maxSelections: 1 },
    { groupId: "g-multi", name: "Topping", maxSelections: 2 },
  ];

  const exceeded = getExceededModifierGroups(groups, {
    "g-single": new Set(["m-1", "m-2"]),
    "g-multi": new Set(["t-1", "t-2"]),
  });

  assert.equal(exceeded.length, 1);
  assert.equal(exceeded[0].groupId, "g-single");
});

test("validateModifierSelections returns valid when item has no modifier groups", () => {
  const result = validateModifierSelections([], {});
  assert.equal(result.isValid, true);
  assert.deepEqual(result.unmetGroups, []);
  assert.equal(result.errorMessage, null);

  const nullResult = validateModifierSelections(null, {});
  assert.equal(nullResult.isValid, true);
});

test("validateModifierSelections allows zero selections for completely optional groups", () => {
  const optionalGroups = [
    { groupId: "g-opt-1", name: "Topping", minSelections: 0, maxSelections: 3 },
    { groupId: "g-opt-2", name: "Đá riêng", minSelections: 0, maxSelections: 1 },
  ];

  const result = validateModifierSelections(optionalGroups, {});
  assert.equal(result.isValid, true);
  assert.deepEqual(result.unmetGroups, []);
  assert.equal(result.errorMessage, null);
});

test("validateModifierSelections blocks addition when mandatory group has unmet selections", () => {
  const groups = [
    { groupId: "g-size", name: "Kích cỡ", minSelections: 1, maxSelections: 1 },
    { groupId: "g-sugar", name: "Độ ngọt", minSelections: 1, maxSelections: 1 },
    { groupId: "g-opt", name: "Topping", minSelections: 0, maxSelections: 5 },
  ];

  // Neither mandatory group selected
  const resEmpty = validateModifierSelections(groups, {});
  assert.equal(resEmpty.isValid, false);
  assert.equal(resEmpty.unmetGroups.length, 2);
  assert.equal(resEmpty.errorMessage, "Vui lòng chọn đủ tùy chọn cho: Kích cỡ");

  // Only size selected
  const resPartial = validateModifierSelections(groups, {
    "g-size": new Set(["m-size-l"]),
  });
  assert.equal(resPartial.isValid, false);
  assert.equal(resPartial.unmetGroups.length, 1);
  assert.equal(resPartial.unmetGroups[0].name, "Độ ngọt");
  assert.equal(resPartial.errorMessage, "Vui lòng chọn đủ tùy chọn cho: Độ ngọt");

  // Both mandatory groups selected, optional group omitted
  const resComplete = validateModifierSelections(groups, {
    "g-size": new Set(["m-size-l"]),
    "g-sugar": new Set(["m-sugar-50"]),
  });
  assert.equal(resComplete.isValid, true);
  assert.deepEqual(resComplete.unmetGroups, []);
  assert.equal(resComplete.errorMessage, null);
});

test("validateModifierSelections blocks addition when selections exceed maxSelections", () => {
  const groups = [
    { groupId: "g-flavor", name: "Hương vị", minSelections: 1, maxSelections: 1 },
  ];

  const resExceeded = validateModifierSelections(groups, {
    "g-flavor": new Set(["vanilla", "chocolate"]),
  });

  assert.equal(resExceeded.isValid, false);
  assert.equal(resExceeded.errorMessage, "Vui lòng chọn tối đa 1 tùy chọn cho: Hương vị");
});

test("validateModifierSelections enforces minSelections > 1 when specified", () => {
  const groups = [
    { groupId: "g-combo", name: "Món phụ chọn 2", minSelections: 2, maxSelections: 3 },
  ];

  const res1 = validateModifierSelections(groups, {
    "g-combo": new Set(["m-1"]),
  });
  assert.equal(res1.isValid, false);
  assert.equal(res1.unmetGroups.length, 1);

  const res2 = validateModifierSelections(groups, {
    "g-combo": new Set(["m-1", "m-2"]),
  });
  assert.equal(res2.isValid, true);
});

test("getModifierGroupBadgeText formats informative badge text for mandatory and optional groups", () => {
  assert.equal(
    getModifierGroupBadgeText({ minSelections: 1 }),
    "(Bắt buộc - Chọn tối thiểu 1)"
  );
  assert.equal(
    getModifierGroupBadgeText({ minSelections: 2 }),
    "(Bắt buộc - Chọn tối thiểu 2)"
  );
  assert.equal(getModifierGroupBadgeText({ minSelections: 0 }), null);
  assert.equal(getModifierGroupBadgeText({}), null);
  assert.equal(getModifierGroupBadgeText(null), null);
});

test("getDefaultModifiersMap correctly extracts default selections from modifier groups", () => {
  const groups = [
    {
      groupId: "g-sugar",
      name: "Độ ngọt",
      minSelections: 1,
      maxSelections: 1,
      modifiers: [
        { modifierId: "s-0", name: "0% đường", isDefault: false },
        { modifierId: "s-50", name: "50% đường", isDefault: false },
        { modifierId: "s-100", name: "100% đường (chuẩn)", isDefault: true },
      ],
    },
    {
      groupId: "g-ice",
      name: "Mức đá",
      minSelections: 1,
      maxSelections: 1,
      modifiers: [
        { modifierId: "i-0", name: "Không đá", isDefault: false },
        { modifierId: "i-100", name: "Đá chung (chuẩn)", isDefault: true },
      ],
    },
    {
      groupId: "g-topping",
      name: "Topping thêm",
      minSelections: 0,
      maxSelections: 3,
      modifiers: [
        { modifierId: "t-pearl", name: "Trân châu đen", isDefault: false },
        { modifierId: "t-pudding", name: "Pudding", isDefault: false },
      ],
    },
  ];

  const defaultMap = getDefaultModifiersMap(groups);

  assert.ok(defaultMap["g-sugar"] instanceof Set);
  assert.equal(defaultMap["g-sugar"].size, 1);
  assert.ok(defaultMap["g-sugar"].has("s-100"));

  assert.ok(defaultMap["g-ice"] instanceof Set);
  assert.equal(defaultMap["g-ice"].size, 1);
  assert.ok(defaultMap["g-ice"].has("i-100"));

  // Optional group with no defaults should not have empty entries or should have empty Set
  assert.ok(!defaultMap["g-topping"] || defaultMap["g-topping"].size === 0);

  // Validate that default selections satisfy mandatory validation right away!
  const validation = validateModifierSelections(groups, defaultMap);
  assert.equal(validation.isValid, true);
  assert.equal(validation.errorMessage, null);
});

test("getDefaultModifiersMap handles null, empty, or missing modifier groups gracefully", () => {
  assert.deepEqual(getDefaultModifiersMap([]), {});
  assert.deepEqual(getDefaultModifiersMap(null), {});
  assert.deepEqual(getDefaultModifiersMap(undefined), {});

  const groupsWithoutModifiers = [{ groupId: "g-empty", modifiers: null }];
  assert.deepEqual(getDefaultModifiersMap(groupsWithoutModifiers), {});
});
