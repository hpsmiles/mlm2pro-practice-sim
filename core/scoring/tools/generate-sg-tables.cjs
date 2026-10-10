#!/usr/bin/env node
// Generates SgTables.kt from sg_reference_full.csv (source: tapinstats.com, user-owned).
// The CSV labels every row with distance_unit (m, ft, yd). Rows with unit "m" are
// canonical (true metres); the ft/yd rows are imperial duplicates of the same grids
// and are skipped by the unit filter below.
// Usage: node core/scoring/tools/generate-sg-tables.cjs   (runs from any directory)
const fs = require("fs");
const path = require("path");

const BANDS = ["tour", "scratch", "hcp10", "hcp18"];
const LIES = ["putt", "fairway", "rough", "tee"];
const BAND_CONST = { tour: "TOUR", scratch: "SCRATCH", hcp10: "HCP10", hcp18: "HCP18" };
const LIE_CONST = { putt: "PUTT", fairway: "FAIRWAY", rough: "ROUGH", tee: "TEE" };

const csvPath = path.join(__dirname, "sg_reference_full.csv");
const lines = fs.readFileSync(csvPath, "utf8").split(/\r?\n/).filter((l) => l.trim().length > 0);
const header = lines.shift();
if (!header.startsWith("skill_level,lie_type")) throw new Error("unexpected header: " + header);

const data = {};
for (const line of lines) {
  const [band, lie, unit, dStr, eStr] = line.split(",");
  // Only unit-m rows are canonical; the ft/yd imperial duplicates are skipped.
  if (unit !== "m") continue;
  if (!LIES.includes(lie) || !BANDS.includes(band)) continue;
  (data[lie] ??= {});
  (data[lie][band] ??= []).push([parseFloat(dStr), parseFloat(eStr)]);
}

for (const lie of LIES) {
  for (const band of BANDS) {
    const rows = data[lie][band];
    // Unit-m distances must be strictly ascending; a decrease signals bad CSV data.
    for (let i = 1; i < rows.length; i++) {
      if (!(rows[i][0] > rows[i - 1][0])) {
        throw new Error(
          `non-ascending unit-m distances for ${band}/${lie} at index ${i}: ` +
            `${rows[i - 1][0]} -> ${rows[i][0]}`,
        );
      }
    }
  }
  const grid = data[lie][BANDS[0]].map((r) => r[0]);
  for (const band of BANDS) {
    const g = data[lie][band].map((r) => r[0]);
    if (g.length !== grid.length || g.some((v, i) => Math.abs(v - grid[i]) > 1e-9)) {
      throw new Error(`grid mismatch for ${lie}/${band}`);
    }
  }
}

const fmt = (v) => {
  let s = Number(v.toFixed(6)).toString();
  if (!s.includes(".") && !s.includes("e")) s += ".0";
  return s;
};
const fmtArr = (arr) => arr.map(fmt).join(", ");

let out = "";
out += "// GENERATED FILE - DO NOT EDIT BY HAND.\n";
out += "// Source: core/scoring/tools/sg_reference_full.csv (tapinstats.com, user-owned), unit-m rows (true metres) only.\n";
out += "// Regenerate with: node core/scoring/tools/generate-sg-tables.cjs\n";
out += "package com.hpsmiles.golfsim.core.scoring\n\n";
out += "/** Expected-strokes grids per lie (one shared distance axis) plus values per skill band. */\n";
out += "object SgTables {\n";
for (const lie of LIES) {
  out += `    val ${LIE_CONST[lie]}_DISTANCES: DoubleArray = doubleArrayOf(${fmtArr(data[lie][BANDS[0]].map((r) => r[0]))})\n`;
  for (const band of BANDS) {
    out += `    val ${LIE_CONST[lie]}_${BAND_CONST[band]}: DoubleArray = doubleArrayOf(${fmtArr(data[lie][band].map((r) => r[1]))})\n`;
  }
}
out += "}\n";

const outPath = path.join(__dirname, "..", "src", "main", "kotlin", "com", "hpsmiles", "golfsim", "core", "scoring", "SgTables.kt");
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, out);
console.log("wrote " + outPath);
