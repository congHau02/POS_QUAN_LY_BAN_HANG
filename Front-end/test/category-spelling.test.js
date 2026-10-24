import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("EditCategoryModal has correct Vietnamese spelling for title and placeholder", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "features", "Category", "EditCategoryModal.jsx"),
    "utf-8"
  );

  assert.equal(
    content.includes("Chỉnh sửa danh mục"),
    true,
    "Should contain 'Chỉnh sửa danh mục'"
  );
  assert.equal(
    content.includes("Chỉnh sủa danh mục"),
    false,
    "Should not contain 'Chỉnh sủa danh mục'"
  );
  assert.equal(
    content.includes("Nhập mô tả danh mục"),
    true,
    "Should contain 'Nhập mô tả danh mục'"
  );
  assert.equal(
    content.includes("Nhập mõ tả danh mục"),
    false,
    "Should not contain 'Nhập mõ tả danh mục'"
  );
});
