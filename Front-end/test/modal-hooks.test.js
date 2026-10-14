import assert from "node:assert/strict";
import test from "node:test";
import { useLockBodyScroll } from "../src/hooks/useLockBodyScroll.js";
import { useEscapeKey } from "../src/hooks/useEscapeKey.js";
import { useOutsideClick } from "../src/hooks/useOutsideClick.js";

test("modal hooks are exported as functions", () => {
  assert.equal(typeof useLockBodyScroll, "function");
  assert.equal(typeof useEscapeKey, "function");
  assert.equal(typeof useOutsideClick, "function");
});
