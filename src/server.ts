import "dotenv/config";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
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

  const sheetMatch = /sheet\s+[""]?([\w \-#]+)[""]?/i.exec(prompt);
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
const server = new McpServer({ name: "autofusion-mcp", version: "0.2.0" });

server.registerTool(
  "autofusion_compare",
  {
    title: "Autofusion Compare",
    description:
      "Unified comparison. Provide a natural-language 'prompt' and/or structured fields. " +
      "Call with dryRun=true first; if status=need_info, ask the user the returned questions; " +
      "if status=ok, call again with dryRun=false using 'normalizedArgs'.",
    inputSchema: CompareArgs.shape,
  },
  async (input: z.infer<typeof CompareArgs>) => {
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

    const result = stdout ? JSON.parse(stdout) : {};

    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      structuredContent: result
    };
  }
);

// Start the server
async function startServer() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("[autofusion-mcp] unified router ready");
}

startServer().catch(console.error);