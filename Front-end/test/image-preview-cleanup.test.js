import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("EditCategoryModal.jsx revokes object URL blobs on unmount or previewUrl change", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "features", "Category", "EditCategoryModal.jsx"),
    "utf-8"
  );

  const hasCleanupEffect =
    /useEffect\s*\(\s*\(\)\s*=>\s*\{[\s\S]*?return\s*\(\)\s*=>\s*\{[\s\S]*?URL\.revokeObjectURL\([\s\S]*?\}\s*;\s*\}\s*,\s*\[\s*previewUrl\s*\]\s*\)/.test(
      content
    );

  assert.equal(
    hasCleanupEffect,
    true,
    "EditCategoryModal must include a useEffect cleanup revoking previewUrl blob on unmount"
  );
});

test("EditItemModal.jsx revokes object URL blobs on unmount or previewUrl change", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "features", "Items", "EditItemModal.jsx"),
    "utf-8"
  );

  const hasCleanupEffect =
    /useEffect\s*\(\s*\(\)\s*=>\s*\{[\s\S]*?return\s*\(\)\s*=>\s*\{[\s\S]*?URL\.revokeObjectURL\([\s\S]*?\}\s*;\s*\}\s*,\s*\[\s*previewUrl\s*\]\s*\)/.test(
      content
    );

  assert.equal(
    hasCleanupEffect,
    true,
    "EditItemModal must include a useEffect cleanup revoking previewUrl blob on unmount"
  );
});
