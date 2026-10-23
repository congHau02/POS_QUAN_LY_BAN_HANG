import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("CategoryService returns unwrapped response.data for addCategory and deleteCategory", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "services", "CategoryService.js"),
    "utf-8"
  );

  assert.equal(
    content.includes("return await api.post"),
    false,
    "addCategory should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    content.includes("return await api.delete"),
    false,
    "deleteCategory should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    /addCategory\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "addCategory should return response.data"
  );
  assert.equal(
    /deleteCategory\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "deleteCategory should return response.data"
  );
});

test("ItemService returns unwrapped response.data for addItem and deleteItem", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "services", "ItemService.js"),
    "utf-8"
  );

  assert.equal(
    content.includes("return await api.post"),
    false,
    "addItem should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    content.includes("return await api.delete"),
    false,
    "deleteItem should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    /addItem\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "addItem should return response.data"
  );
  assert.equal(
    /deleteItem\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "deleteItem should return response.data"
  );
});

test("UserService returns unwrapped response.data for addUser and deleteUser", () => {
  const content = fs.readFileSync(
    path.join(srcDir, "services", "UserService.js"),
    "utf-8"
  );

  assert.equal(
    content.includes("return await api.post"),
    false,
    "addUser should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    content.includes("return await api.delete"),
    false,
    "deleteUser should unwrap response.data instead of returning raw api promise"
  );
  assert.equal(
    /addUser\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "addUser should return response.data"
  );
  assert.equal(
    /deleteUser\s*=\s*async[\s\S]*?response\.data/.test(content),
    true,
    "deleteUser should return response.data"
  );
});
