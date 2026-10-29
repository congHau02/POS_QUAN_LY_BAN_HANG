import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  getDefaultModifiersMap,
  validateModifierSelections,
} from "../src/utils/modifierValidation.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("default modifiers satisfy mandatory modifier groups without manual selection", () => {
  const item = {
    itemId: "item-tra-sua",
    name: "Trà sữa trân châu",
    price: 30000,
    modifierGroups: [
      {
        groupId: "g-sugar",
        name: "Mức đường",
        minSelections: 1,
        maxSelections: 1,
        modifiers: [
          { modifierId: "s-0", name: "0% đường", isDefault: false },
          { modifierId: "s-50", name: "50% đường", isDefault: false },
          { modifierId: "s-100", name: "100% đường", isDefault: true },
        ],
      },
      {
        groupId: "g-ice",
        name: "Mức đá",
        minSelections: 1,
        maxSelections: 1,
        modifiers: [
          { modifierId: "i-0", name: "Không đá", isDefault: false },
          { modifierId: "i-100", name: "100% đá", isDefault: true },
        ],
      },
    ],
  };

  // Initial state should extract default modifiers
  const initialSelectedMap = getDefaultModifiersMap(item.modifierGroups);

  assert.equal(initialSelectedMap["g-sugar"].has("s-100"), true);
  assert.equal(initialSelectedMap["g-ice"].has("i-100"), true);

  // Directly validating the pre-selected defaults succeeds immediately
  const validation = validateModifierSelections(item.modifierGroups, initialSelectedMap);
  assert.equal(validation.isValid, true);
  assert.equal(validation.errorMessage, null);
});

test("POSItemModal.jsx integrates getDefaultModifiersMap and keyboard shortcuts", () => {
  const modalPath = path.resolve(__dirname, "../src/features/Explore/POSItemModal.jsx");
  const modalContent = fs.readFileSync(modalPath, "utf-8");

  assert.ok(
    modalContent.includes("getDefaultModifiersMap"),
    "POSItemModal must import and use getDefaultModifiersMap"
  );
  assert.ok(
    modalContent.includes("Enter") && modalContent.includes("Escape"),
    "POSItemModal must support Enter and Escape keyboard shortcuts"
  );
  assert.ok(
    modalContent.includes("isDefault"),
    "POSItemModal must render visual indicator for default modifiers"
  );
});

test("ModifierGroupForm.jsx and ModifierGroupList.jsx support isDefault configuration and display", () => {
  const formPath = path.resolve(__dirname, "../src/features/Modifiers/ModifierGroupForm.jsx");
  const formContent = fs.readFileSync(formPath, "utf-8");

  assert.ok(
    formContent.includes("isDefault"),
    "ModifierGroupForm must include isDefault in form state and payload"
  );
  assert.ok(
    formContent.includes("handleToggleDefault"),
    "ModifierGroupForm must provide handleToggleDefault handler"
  );

  const listPath = path.resolve(__dirname, "../src/features/Modifiers/ModifierGroupList.jsx");
  const listContent = fs.readFileSync(listPath, "utf-8");

  assert.ok(
    listContent.includes("isDefault"),
    "ModifierGroupList must show badge for default modifier"
  );
});
