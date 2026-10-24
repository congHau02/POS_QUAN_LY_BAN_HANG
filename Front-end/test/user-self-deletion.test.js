import assert from "node:assert/strict";
import test from "node:test";
import { isSelfAccount } from "../src/features/Users/userUtils.js";

test("isSelfAccount returns true when target user matches current user email", () => {
  const currentUser = { email: "admin@example.com", name: "Admin" };
  const targetUser = { email: "admin@example.com", name: "Admin" };

  assert.equal(isSelfAccount(targetUser, currentUser), true);
});

test("isSelfAccount is case-insensitive and trims whitespace", () => {
  const currentUser = { email: "Admin@Example.COM " };
  const targetUser = { email: " admin@example.com" };

  assert.equal(isSelfAccount(targetUser, currentUser), true);
});

test("isSelfAccount returns false when emails differ", () => {
  const currentUser = { email: "admin@example.com" };
  const otherUser = { email: "staff@example.com" };

  assert.equal(isSelfAccount(otherUser, currentUser), false);
});

test("isSelfAccount returns false when targetUser or currentUser is missing or has no email", () => {
  assert.equal(isSelfAccount(null, { email: "admin@example.com" }), false);
  assert.equal(isSelfAccount({ email: "admin@example.com" }, null), false);
  assert.equal(isSelfAccount({}, { email: "admin@example.com" }), false);
  assert.equal(isSelfAccount({ email: "admin@example.com" }, {}), false);
  assert.equal(isSelfAccount(null, null), false);
});
