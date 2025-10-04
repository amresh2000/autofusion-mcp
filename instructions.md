Here’s a copy-pasteable **INSTRUCTIONS.md** for your **single-tool** router setup (`autofusion_compare`). It walks from an empty folder to a working MCP server in Copilot, calling your **Autofusion Core** shaded CLI JAR under the hood.

---

# Autofusion-MCP (Single Tool Router) — Instructions

This repo exposes **one** MCP tool, `autofusion_compare`. It accepts a natural-language prompt (plus optional structured fields), figures out whether to run an **Excel**, **CSV**, or **Table** comparison, performs a **dry-run** to gather missing info (asking the user questions), then executes your **Autofusion Core** CLI and returns result file paths.

```
Copilot Chat → MCP tool: autofusion_compare → (router)
                                     ↘ java -jar autofusion-1.0.0-shaded.jar <excel|csv|table> [...]
```

---

Path to my Autofusion 1.0.0 jar is "/Users/amresh/Downloads/autofusion-1.0.0-shaded.jar"

## 1) Create the project

Initialize Node/TypeScript:

```bash
npm init -y
npm i -S @modelcontextprotocol/sdk zod dotenv
npm i -D typescript ts-node @types/node
npx tsc --init
```

Create folders:

```bash
mkdir -p src
```

Create `.gitignore`:

```
node_modules
dist
.env
.DS_Store
```

---

## 2) Configure TypeScript

`tsconfig.json` (overwrite with this minimal config):

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "CommonJS",
    "outDir": "dist",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src"]
}
```

Update `package.json` scripts:

```json
{
  "name": "autofusion-mcp",
  "version": "0.1.0",
  "private": true,
  "type": "commonjs",
  "scripts": {
    "build": "tsc",
    "start": "node dist/server.js",
    "dev": "ts-node src/server.ts"
  }
}
```

---

## 3) Point to your Core shaded JAR (env)

Create `.env` in project root:

```dotenv
# Path to Java and your shaded Autofusion Core CLI JAR
JAVA_BIN=java
AUTOFUSION_JAR=/ABSOLUTE/PATH/TO/autofusion-1.0.0-shaded.jar
AUTOFUSION_HEAP=-Xmx2g
```

> Windows example:
>
> ```
> AUTOFUSION_JAR=C:\work\autofusion-core\target\autofusion-1.0.0-shaded.jar
> JAVA_BIN=java
> AUTOFUSION_HEAP=-Xmx2g
> ```

**Tip:** Use absolute paths; relative paths can break when VS Code spawns the server.

---

## 4) Implement the MCP server (single tool router)

Create `src/server.ts`:

```ts
import "dotenv/config";
import { Server } from "@modelcontextprotocol/sdk/server";
import { z } from "zod";
import { spawn } from "child_process";

// ---- ENV ----
const JAVA = process.env.JAVA_BIN || "java";
const JAR =
  process.env.AUTOFUSION_JAR || "/path/to/autofusion-1.0.0-shaded.jar";
const HEAP = process.env.AUTOFUSION_HEAP || "-Xmx2g";

// ---- Schema: single tool, natural language + optional fields ----
const CompareArgs = z.object({
  // NL prompt (optional but recommended)
  prompt: z.string().optional(),

  // File-based comparison
  file1: z.string().optional(),
  file2: z.string().optional(),
  sheet: z.string().optional(),
  headerRow: z.number().optional(),
  dataRowStart: z.number().optional(),
  ignoreSheets: z.array(z.string()).optional(),

  // Keys/columns
  keys: z.array(z.string()).optional(),
  ignoreColumns: z.array(z.string()).optional(),

  // Tolerances
  thresholds: z.record(z.number()).optional(),

  // CSV extras
  delimiter: z.string().optional(),
  skipHeader: z.boolean().optional(),

  // Table (inline JSON) forces table mode if both present
  source: z.array(z.record(z.string(), z.string())).optional(),
  target: z.array(z.record(z.string(), z.string())).optional(),

  // Outputs & control
  outDir: z.string().optional(),
  dryRun: z.boolean().optional().default(true),
});

// ---- tiny process runner ----
function runJar(args: string[]) {
  return new Promise<{ code: number; stdout: string; stderr: string }>(
    (resolve) => {
      const ps = spawn(JAVA, [HEAP, "-jar", JAR, ...args], {
        stdio: ["ignore", "pipe", "pipe"],
      });
      let out = "",
        err = "";
      ps.stdout.on("data", (d) => (out += d.toString()));
      ps.stderr.on("data", (d) => (err += d.toString()));
      ps.on("close", (code) =>
        resolve({ code: code ?? 1, stdout: out.trim(), stderr: err.trim() })
      );
    }
  );
}

// ---- heuristics & helpers ----
function looksCsv(p?: string) {
  return !!p && /\.csv$/i.test(p);
}
function looksExcel(p?: string) {
  return !!p && /\.(xlsx|xlsm|xls)$/i.test(p);
}
function csvPair(a?: string, b?: string) {
  return looksCsv(a) && looksCsv(b);
}
function excelPair(a?: string, b?: string) {
  return looksExcel(a) && looksExcel(b);
}

// Extract structured hints from NL prompt (best-effort)
function parseFromPrompt(prompt?: string) {
  if (!prompt) return {};
  const splitCsv = (s?: string) =>
    s
      ? s
          .split(",")
          .map((x) => x.trim())
          .filter(Boolean)
      : undefined;

  const sheetMatch = /sheet\s+["“]?([\w \-#]+)["”]?/i.exec(prompt);
  const keysMatch = /key(?:s)?[^:\w]*[:\- ]+([A-Za-z0-9_, ]+)/i.exec(prompt);

  const thrMatches = [
    ...prompt.matchAll(
      /(\d+(?:\.\d+)?)\s*%\s*(?:on|for)?\s*([A-Za-z0-9_ ]+)/gi
    ),
  ];
  const thresholds: Record<string, number> = {};
  thrMatches.forEach((m) => {
    const pct = parseFloat(m[1]);
    const col = m[2].trim().replace(/\s{2,}/g, " ");
    if (!isNaN(pct) && col) thresholds[col] = pct;
  });

  const ignSheets = /ignore\s+sheets?\s+([A-Za-z0-9_, \-]+)/i.exec(prompt);
  const ignCols = /ignore\s+columns?\s+([A-Za-z0-9_, \-]+)/i.exec(prompt);

  return {
    sheet: sheetMatch?.[1],
    keys: keysMatch
      ? keysMatch[1]
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
      : undefined,
    thresholds: Object.keys(thresholds).length ? thresholds : undefined,
    ignoreSheets: splitCsv(ignSheets?.[1]),
    ignoreColumns: splitCsv(ignCols?.[1]),
  };
}

function toExcelFlags(a: any) {
  const f = [
    "excel",
    "--json",
    `--file1=${a.file1}`,
    `--file2=${a.file2}`,
    `--dryRun=${a.dryRun !== false}`,
  ];
  if (a.sheet) f.push(`--sheet=${a.sheet}`);
  if (a.keys?.length) {
    f.push(`--mode=uniqueKey`);
    f.push(`--keys=${a.keys.join(",")}`);
  }
  if (!a.keys?.length && a.sheet) f.push(`--mode=rowDiff`);
  if (a.headerRow) f.push(`--headerRow=${a.headerRow}`);
  if (a.dataRowStart) f.push(`--dataRowStart=${a.dataRowStart}`);
  if (a.thresholds)
    f.push(`--thresholds=${encodeURIComponent(JSON.stringify(a.thresholds))}`);
  if (a.ignoreSheets?.length)
    f.push(`--ignoreSheets=${a.ignoreSheets.join(",")}`);
  if (a.outDir) f.push(`--out=${a.outDir}`);
  return f;
}
function toCsvFlags(a: any) {
  const f = [
    "csv",
    "--json",
    `--file1=${a.file1}`,
    `--file2=${a.file2}`,
    `--dryRun=${a.dryRun !== false}`,
  ];
  if (a.delimiter) f.push(`--delimiter=${a.delimiter}`);
  if (a.skipHeader !== undefined) f.push(`--skipHeader=${a.skipHeader}`);
  if (a.keys?.length) f.push(`--primaryKey=${a.keys.join(",")}`);
  if (a.ignoreColumns?.length)
    f.push(`--ignoreCols=${a.ignoreColumns.join(",")}`);
  if (a.outDir) f.push(`--out=${a.outDir}`);
  return f;
}
function toTableFlags(a: any) {
  return [
    "table",
    "--json",
    `--source=${encodeURIComponent(JSON.stringify(a.source))}`,
    `--target=${encodeURIComponent(JSON.stringify(a.target))}`,
    `--uniKey=${a.keys?.[0] || "UNL_KEY"}`,
    `--thresholds=${encodeURIComponent(JSON.stringify(a.thresholds || {}))}`,
    `--dryRun=${a.dryRun !== false}`,
  ];
}

// ---- MCP server (single tool) ----
const server = new Server({ name: "autofusion-mcp", version: "0.2.0" });

server.tool(
  "autofusion_compare",
  {
    description:
      "Unified comparison. Provide a natural-language 'prompt' and/or structured fields. " +
      "Call with dryRun=true first; if status=need_info, ask the user the returned questions; " +
      "if status=ok, call again with dryRun=false using 'normalizedArgs'.",
    inputSchema: CompareArgs,
  },
  async (input) => {
    // Merge prompt-derived hints with explicit fields (explicit wins)
    const hints = parseFromPrompt(input.prompt);
    const args = { ...hints, ...input };

    // Route: table (inline JSON) > csv (by extension) > excel (default)
    let mode: "table" | "csv" | "excel";
    if (args.source && args.target) mode = "table";
    else if (csvPair(args.file1, args.file2)) mode = "csv";
    else mode = "excel";

    const flags =
      mode === "table"
        ? toTableFlags(args)
        : mode === "csv"
        ? toCsvFlags(args)
        : toExcelFlags(args);

    const { code, stdout, stderr } = await runJar(flags);
    if (code !== 0) throw new Error(stderr || "Autofusion CLI error");
    return stdout ? JSON.parse(stdout) : {};
  }
);

server
  .start()
  .then(() => console.error("[autofusion-mcp] unified router ready"));
```

---

## 5) Build & run locally

Install deps and build:

```bash
npm i
npm run build
```

Sanity-run the server (must print “ready” to **stderr**):

```bash
JAVA_BIN=java AUTOFUSION_JAR=/abs/path/to/autofusion-1.0.0-shaded.jar npm run start
```

(Stop with `Ctrl+C`.)

---

## 6) Register the MCP server in VS Code Copilot

Depending on your Copilot build, add an MCP configuration entry (via settings or JSON). Typical JSON example:

```json
{
  "servers": {
    "autofusion-mcp": {
      "command": "node",
      "args": ["/ABS/PATH/autofusion-mcp/dist/server.js"],
      "env": {
        "JAVA_BIN": "java",
        "AUTOFUSION_JAR": "/ABS/PATH/autofusion-1.0.0-shaded.jar",
        "AUTOFUSION_HEAP": "-Xmx2g"
      }
    }
  }
}
```

Restart VS Code. In Copilot Chat, list tools—you should see **`autofusion_compare`**.

---

## 7) First end-to-end test (dry-run → confirm → run)

In Copilot Chat, try:

> Compare `C:\data\A.xlsx` vs `C:\data\B.xlsx`, sheet `Trades`. Keys `TradeId,AsOfDate`. 2% on `Amount`. Ignore sheet `Archive`. Output to `C:\out`.

Expected flow:

1. Copilot calls `autofusion_compare` with `dryRun=true`.
2. The CLI (via MCP) validates; if any info missing (e.g., sheet not specified or ambiguous), it returns:

   ```json
   {
     "status": "need_info",
     "questions": [{ "id": "sheet", "choices": ["Trades", "Archive"] }],
     "hints": { "headerRow": 1, "dataRowStart": 2 }
   }
   ```

   Copilot will ask you those questions.

3. When sufficient, CLI returns:

   ```json
   { "status":"ok", "nextAction":"confirm", "normalizedArgs":{...}, "summary":"Compare A vs B on 'Trades' ..." }
   ```

   Copilot asks “Run now?”.

4. You say “yes”; Copilot calls again with `dryRun=false` and the `normalizedArgs`.
5. Execution returns:

   ```json
   {
     "same": false,
     "summaryPath": "C:\\out\\...-summary.xlsx",
     "detailPath": "C:\\out\\...-detail.xlsx",
     "logPath": "C:\\out\\diffs.xlsx",
     "elapsedMs": 1140
   }
   ```

   Copilot can open those files.

**CSV test**:

> Compare `C:\data\a.csv` vs `C:\data\b.csv` using key `account_id`. Ignore columns `updated_at,ingest_ts`.

**Table test (inline JSON)**:

> Compare these two tables by `UNL_KEY`:
> `source=[{"UNL_KEY":"1","amt":"100"},{"UNL_KEY":"2","amt":"200"}]` > `target=[{"UNL_KEY":"1","amt":"100"},{"UNL_KEY":"2","amt":"205"}]` > `thresholds={"amt":2}`

---

## 8) Operational polish (highly recommended)

- **Timeouts:** Wrap `runJar` in a timeout (kill after N minutes for very large files).
- **Path allow-list:** Optionally restrict `file1/file2/outDir` to specific base directories to avoid accidental access.
- **Logging:** Keep **stdout** as JSON only. Send diagnostics to **stderr** in the Java CLI (`slf4j-simple` default WARN/ERROR).
- **Memory:** Tune `AUTOFUSION_HEAP` (e.g., `-Xmx4g`) for huge workbooks.
- **Stable filenames:** Your CLI should create a run folder (e.g., `outDir/<timestamp>/`) and write `summary.xlsx`, `detail.xlsx`, `diffs.xlsx` there consistently.
- **Error mapping:** Convert common causes to helpful messages:

  - Sheet not found → list available sheets
  - Header/data row mismatch → suggest 1/2 default
  - Key columns not present → list closest matches

---

## 9) Repo structure (final)

```
autofusion-mcp/
├─ .env
├─ .gitignore
├─ package.json
├─ tsconfig.json
├─ src/
│  └─ server.ts
└─ dist/ (generated by build)
```

(You can split helpers into `schema.ts`, `exec.ts`, `mappers.ts` later; the single-file `server.ts` is fine for POC.)

---

## 10) Troubleshooting

- **Tools not visible in Copilot:**
  Verify the MCP config path, restart VS Code, ensure the server prints “ready” on stderr, and the process is running.
- **“Java not found”:**
  Ensure `JAVA_BIN` points to `java` in PATH or the full path to `java.exe`.
- **“JAR not found” / wrong path:**
  The MCP env must use **absolute** paths for `AUTOFUSION_JAR`.
- **CLI returning non-JSON:**
  Ensure your CLI prints **only JSON** to stdout (`System.out.println(json)`), and logs to stderr.
- **Large sheets OOM:**
  Increase `AUTOFUSION_HEAP`, or optimize Core readers (streaming/POI SXSSF if needed).

---

## 11) Rollout checklist

- [ ] Shaded JAR built and tested manually with `java -jar ...`.
- [ ] MCP server runs locally (`npm run start`) and prints “ready”.
- [ ] Copilot can see `autofusion_compare`.
- [ ] Dry-run → confirm → run loop works for Excel and CSV.
- [ ] Table (inline JSON) path verified.
- [ ] Basic safety (timeouts, allow-list) added.
- [ ] README with sample prompts and known limitations committed.

---

## 12) Example prompts to share with users

- “Compare `A.xlsx` and `B.xlsx` on **sheet** `Trades`. **Keys** `TradeId,AsOfDate`. **2% on Amount**. **Ignore sheet** `Archive`. **Output** to `C:\out`.”
- “Compare `in.csv` vs `out.csv` **using key** `account_id`. **Ignore columns** `updated_at,ingest_ts`.”
- “Compare these two **tables** by `UNL_KEY`: `source=[...]`, `target=[...]`. **1% tolerance on balance**.”

---

That’s everything you need—from mkdir to opening results from Copilot. If you want, I can also hand you a minimal **GitHub Actions** (or your internal CI) workflow to build the MCP server and verify a smoke test against a small pair of workbooks.
