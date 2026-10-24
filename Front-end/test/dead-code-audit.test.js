import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, "../src");

test("dead code files are deleted from disk", () => {
  const prunedFiles = [
    path.join(srcDir, "ui", "Modal.jsx"),
    path.join(srcDir, "features", "Modifiers", "useUpdateModifierGroup.js"),
    path.join(srcDir, "pages", "Explore", "Explore.css"),
    path.join(srcDir, "App.css"),
  ];

  for (const filePath of prunedFiles) {
    assert.equal(
      fs.existsSync(filePath),
      false,
      `Expected ${path.relative(srcDir, filePath)} to be pruned, but file exists.`
    );
  }
});

test("App.jsx does not import legacy App.css", () => {
  const appPath = path.join(srcDir, "App.jsx");
  const content = fs.readFileSync(appPath, "utf-8");
  assert.equal(
    content.includes('import "./App.css";') || content.includes("import './App.css';"),
    false,
    "App.jsx should not import App.css"
  );
});

test("no source files reference pruned files", () => {
  function scanDir(dir) {
    const files = fs.readdirSync(dir, { withFileTypes: true });
    for (const file of files) {
      const fullPath = path.join(dir, file.name);
      if (file.isDirectory()) {
        scanDir(fullPath);
      } else if (file.name.endsWith(".js") || file.name.endsWith(".jsx")) {
        const text = fs.readFileSync(fullPath, "utf-8");
        assert.equal(
          text.includes("useUpdateModifierGroup"),
          false,
          `File ${path.relative(srcDir, fullPath)} still references useUpdateModifierGroup`
        );
        assert.equal(
          text.includes("Explore.css"),
          false,
          `File ${path.relative(srcDir, fullPath)} still references Explore.css`
        );
        assert.equal(
          text.includes("ui/Modal"),
          false,
          `File ${path.relative(srcDir, fullPath)} still references ui/Modal`
        );
      }
    }
  }

  scanDir(srcDir);
});
