import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { useLockBodyScroll } from "../src/hooks/useLockBodyScroll.js";
import { useEscapeKey } from "../src/hooks/useEscapeKey.js";
import { useOutsideClick } from "../src/hooks/useOutsideClick.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("modal hooks are exported as functions", () => {
  assert.equal(typeof useLockBodyScroll, "function");
  assert.equal(typeof useEscapeKey, "function");
  assert.equal(typeof useOutsideClick, "function");
});

test("ConfirmDeleteModal reuses shared useLockBodyScroll and useEscapeKey hooks", () => {
  const modalPath = path.join(srcDir, "ui", "ConfirmDeleteModal.jsx");
  const content = fs.readFileSync(modalPath, "utf-8");

  assert.equal(
    content.includes('useLockBodyScroll') &&
      content.includes('useEscapeKey') &&
      !content.includes('document.body.style.overflow') &&
      !content.includes('window.addEventListener("keydown"'),
    true,
    "ConfirmDeleteModal must use shared hooks and not manually attach listeners"
  );
});
