import "dotenv/config";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  ListToolsRequestSchema,
  CallToolRequestSchema
} from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import { zodToJsonSchema } from "zod-to-json-schema";
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

  // Excel-specific enhancements
  mode: z.enum(["cellByCell", "rowDiff", "uniqueKey"]).optional().describe("Excel comparison mode: cellByCell|rowDiff|uniqueKey (recommended: uniqueKey)"),
  headerRow: z.number().optional().describe("Row number containing column headers (1-based, default: 1)"),
  dataRowStart: z.number().optional().describe("First data row number (1-based, default: 2)"),
  ignoreSheets: z.array(z.string()).optional().describe("Sheet names to ignore during comparison"),

  // Database comparison parameters
  sourceDb: z.string().optional().describe("Source database connection string: postgresql://user:pass@host:port/db or jdbcUrl;user;pass;driver"),
  targetDb: z.string().optional().describe("Target database connection string: postgresql://user:pass@host:port/db or jdbcUrl;user;pass;driver"),
  sourceSql: z.string().optional().describe("SQL query to execute on source database"),
  targetSql: z.string().optional().describe("SQL query to execute on target database"),
  uniqueKey: z.string().optional().describe("Unique key column name for database comparison matching"),
  testConnection: z.boolean().optional().describe("Test database connections without executing queries"),

  // Keys/columns (enhanced for composite key support)
  keys: z.array(z.string()).optional().describe("Key columns for uniqueKey mode - supports composite keys"),
  ignoreColumns: z.array(z.string()).optional(),

  // Tolerances
  thresholds: z.record(z.number()).optional().describe("Numeric comparison thresholds, e.g. {\"Amount\":2.0}"),

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

  // File-based patterns
  const sheetMatch = /sheet\s+[""]?([\w \-#]+)[""]?/i.exec(prompt);
  const keysMatch = /(?:key(?:s)?|composite key(?:s)?)[^:\w]*[:\- ]+([A-Za-z0-9_, ]+)/i.exec(prompt);

  // Database patterns
  const sourceDbMatch = /(?:source|from)\s+(?:database|db)[:\s]+([^\s;]+(?:;[^;]+;[^;]+(?:;[^\s]+)?)?)/i.exec(prompt);
  const targetDbMatch = /(?:target|to)\s+(?:database|db)[:\s]+([^\s;]+(?:;[^;]+;[^;]+(?:;[^\s]+)?)?)/i.exec(prompt);
  const sourceSqlMatch = /(?:source|from)\s+(?:sql|query)[:\s]+["']([^"']+)["']/i.exec(prompt);
  const targetSqlMatch = /(?:target|to)\s+(?:sql|query)[:\s]+["']([^"']+)["']/i.exec(prompt);
  const uniqueKeyMatch = /(?:unique|primary)\s+key[:\s]+([A-Za-z0-9_]+)/i.exec(prompt);
  const testConnectionMatch = /test\s+connection/i.test(prompt);

  // Excel mode parsing
  const modeMatch = /mode[:\s]+(cellByCell|rowDiff|uniqueKey)/i.exec(prompt);
  const cellByCellMatch = /cell\s*by\s*cell|cell-by-cell/i.test(prompt);
  const excelUniqueKeyMatch = /unique\s*key|key\s*based/i.test(prompt);

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

  // Row configuration parsing
  const headerRowMatch = /header\s+row\s+(\d+)/i.exec(prompt);
  const dataRowMatch = /data\s+(?:starts?|row)\s+(\d+)/i.exec(prompt);

  const ignSheets = /ignore\s+sheets?\s+([A-Za-z0-9_, \-]+)/i.exec(prompt);
  const ignCols = /ignore\s+columns?\s+([A-Za-z0-9_, \-]+)/i.exec(prompt);

  // Determine Excel mode from natural language hints
  let mode: "cellByCell" | "rowDiff" | "uniqueKey" | undefined;
  if (modeMatch?.[1]) {
    mode = modeMatch[1].toLowerCase() as "cellByCell" | "rowDiff" | "uniqueKey";
  } else if (cellByCellMatch) {
    mode = "cellByCell";
  } else if (excelUniqueKeyMatch || keysMatch) {
    mode = "uniqueKey";
  }

  return {
    // File-based extraction
    sheet: sheetMatch?.[1],
    mode,
    keys: keysMatch
      ? keysMatch[1]
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
      : undefined,
    headerRow: headerRowMatch ? parseInt(headerRowMatch[1]) : undefined,
    dataRowStart: dataRowMatch ? parseInt(dataRowMatch[1]) : undefined,
    thresholds: Object.keys(thresholds).length ? thresholds : undefined,
    ignoreSheets: splitCsv(ignSheets?.[1]),
    ignoreColumns: splitCsv(ignCols?.[1]),

    // Database extraction
    sourceDb: sourceDbMatch?.[1],
    targetDb: targetDbMatch?.[1],
    sourceSql: sourceSqlMatch?.[1],
    targetSql: targetSqlMatch?.[1],
    uniqueKey: uniqueKeyMatch?.[1],
    testConnection: testConnectionMatch,
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

  // Excel mode support (enhanced)
  if (a.mode) {
    f.push(`--mode=${a.mode}`);
  } else if (a.keys?.length) {
    // Auto-detect uniqueKey mode when keys are provided
    f.push(`--mode=uniqueKey`);
  } else {
    // Default to rowDiff mode
    f.push(`--mode=rowDiff`);
  }

  // Sheet and key parameters
  if (a.sheet) f.push(`--sheet=${a.sheet}`);
  if (a.keys?.length) f.push(`--keys=${a.keys.join(",")}`);

  // Excel-specific row configuration
  if (a.headerRow) f.push(`--headerRow=${a.headerRow}`);
  if (a.dataRowStart) f.push(`--dataRowStart=${a.dataRowStart}`);

  // Thresholds and ignore options
  if (a.thresholds)
    f.push(`--thresholds=${encodeURIComponent(JSON.stringify(a.thresholds))}`);
  if (a.ignoreSheets?.length)
    f.push(`--ignoreSheets=${a.ignoreSheets.join(",")}`);

  // Output directory
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
    `--uniKey=${a.keys?.[0] || a.uniqueKey || "UNL_KEY"}`,
    `--thresholds=${encodeURIComponent(JSON.stringify(a.thresholds || {}))}`,
    `--dryRun=${a.dryRun !== false}`,
  ];
}

function toDbFlags(a: any) {
  const f = [
    "db",
    "--json",
    `--source=${a.sourceDb}`,
    `--target=${a.targetDb}`,
    `--sourceSql=${encodeURIComponent(a.sourceSql)}`,
    `--targetSql=${encodeURIComponent(a.targetSql)}`,
    `--uniqueKey=${a.uniqueKey}`,
    `--dryRun=${a.dryRun !== false}`,
  ];

  // Add optional parameters
  if (a.thresholds)
    f.push(`--thresholds=${encodeURIComponent(JSON.stringify(a.thresholds))}`);
  if (a.ignoreColumns?.length)
    f.push(`--ignoreColumns=${a.ignoreColumns.join(",")}`);
  if (a.outDir) f.push(`--out=${a.outDir}`);
  if (a.testConnection) f.push(`--testConnection=true`);

  return f;
}

// ---- Response classification and formatting ----
function formatNeedInfoResponse(jarResponse: any) {
  const questions = jarResponse.questions || [];
  const questionsText = questions.map((q: any) => {
    let text = `• ${q.text}`;
    if (q.choices && q.choices.length > 0) {
      if (q.suggested && q.suggested.length > 0) {
        text += `\n  Suggested: ${q.suggested.join(', ')}`;
      }
      text += `\n  Available: ${q.choices.join(', ')}`;
    }
    if (q.hint) {
      text += `\n  Hint: ${q.hint}`;
    }
    return text;
  }).join('\n\n');

  return {
    content: [{
      type: "text",
      text: `I need more information to proceed:\n\n${questionsText}\n\nPlease provide the missing information and call again.`
    }],
    isError: false
  };
}

function formatConfirmationResponse(jarResponse: any) {
  const summary = jarResponse.summary || "Ready to execute comparison";
  const details = jarResponse.normalizedArgs ?
    `\n\nConfiguration:\n${JSON.stringify(jarResponse.normalizedArgs, null, 2)}` : "";

  return {
    content: [{
      type: "text",
      text: `${summary}${details}\n\nCall again with the same parameters and dryRun=false to execute.`
    }],
    isError: false
  };
}

function formatErrorResponse(message: string) {
  return {
    content: [{
      type: "text",
      text: `Error: ${message}`
    }],
    isError: true
  };
}

// ---- MCP server (single tool) ----
const server = new Server(
  {
    name: "autofusion-mcp",
    version: "0.2.0"
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "autofusion_compare",
      description:
        "Advanced Excel/CSV/JSON/Database comparison with intelligent key detection and interactive question handling. " +
        "Supports database comparisons via SQL queries on PostgreSQL, MySQL, Oracle, SQL Server, and H2. " +
        "Always performs dry-run validation first. When I return 'need_info', please provide the requested information " +
        "and call again. When I return confirmation, call again with dryRun=false to execute the comparison.",
      inputSchema: zodToJsonSchema(CompareArgs),
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== "autofusion_compare") {
    throw new Error(`Unknown tool: ${request.params.name}`);
  }

  const input = request.params.arguments as z.infer<typeof CompareArgs>;

  try {
    // Merge prompt-derived hints with explicit fields (explicit wins)
    const hints = parseFromPrompt(input.prompt);
    const args = { ...hints, ...input };

    // Route: database > table (inline JSON) > csv (by extension) > excel (default)
    let mode: "database" | "table" | "csv" | "excel";
    if (args.sourceDb && args.targetDb && args.sourceSql && args.targetSql && args.uniqueKey) mode = "database";
    else if (args.source && args.target) mode = "table";
    else if (csvPair(args.file1, args.file2)) mode = "csv";
    else mode = "excel";

    // PHASE 1: Always do dry run first (unless explicitly doing execution phase)
    const isDryRun = args.dryRun !== false;

    const flags =
      mode === "database"
        ? toDbFlags({ ...args, dryRun: isDryRun })
        : mode === "table"
        ? toTableFlags({ ...args, dryRun: isDryRun })
        : mode === "csv"
        ? toCsvFlags({ ...args, dryRun: isDryRun })
        : toExcelFlags({ ...args, dryRun: isDryRun });

    const { code, stdout, stderr } = await runJar(flags);
    if (code !== 0) {
      return formatErrorResponse(stderr || "Autofusion CLI error");
    }

    const result = stdout ? JSON.parse(stdout) : {};

    // PHASE 2: Handle response based on status
    switch (result.status) {
      case 'need_info':
        return formatNeedInfoResponse(result);

      case 'ok':
        if (isDryRun) {
          return formatConfirmationResponse(result);
        } else {
          // Execution completed successfully
          return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
            isError: false,
          };
        }

      case 'success':
        // Direct success from execution
        return {
          content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
          isError: false,
        };

      case 'error':
        return formatErrorResponse(result.message || "Comparison failed");

      default:
        // Fallback for unexpected response format
        return {
          content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
          isError: false,
        };
    }

  } catch (error) {
    return formatErrorResponse(error instanceof Error ? error.message : String(error));
  }
});

// Start the server
async function startServer() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("[autofusion-mcp] unified router ready");
}

if (require.main === module) {
  startServer().catch(console.error);
}