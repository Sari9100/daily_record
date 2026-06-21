// family-os-mcp — Family OS 전용 MCP 서버 (Streamable HTTP)
// 설계: scratchpad/deploy-integration-plan.md, phase-drafts.md (Phase 6), member-provisioning.md
//
// 역할: Hermes(구성원별 프로필) → POST /mcp → family-os-api REST 어댑터.
// 신원: 요청 헤더 X-Telegram-User-Id 를 telegramUserId 로 family-os-api 에 전달(LLM 인자 아님 — 위변조 방지).
//       헤더 없으면 401(fail-closed). 아웃바운드는 X-Service-Token.
// ※ SDK 버전별 API 차이 가능 — registerTool / StreamableHTTPServerTransport 시그니처는 설치 버전에 맞춰 조정.

import express from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

const API_BASE = process.env.FAMILYOS_API_BASE_URL ?? "http://family-os-api:8080/api/v1";
const SERVICE_TOKEN = process.env.FAMILYOS_SERVICE_TOKEN ?? "";
const PORT = Number(process.env.PORT ?? 3000);

if (!SERVICE_TOKEN) {
  console.error("[family-os-mcp] FAMILYOS_SERVICE_TOKEN 미설정 — family-os-api 가 401 로 거부합니다.");
}

type Query = Record<string, string | number | boolean | undefined>;

async function callApi(
  method: string,
  path: string,
  opts: { body?: unknown; query?: Query },
): Promise<string> {
  const url = new URL(API_BASE + path);
  if (opts.query) {
    for (const [k, v] of Object.entries(opts.query)) {
      if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
    }
  }
  const res = await fetch(url, {
    method,
    headers: { "X-Service-Token": SERVICE_TOKEN, "Content-Type": "application/json" },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  const text = await res.text();
  return text || `{"success":false,"error":{"message":"empty response (${res.status})"}}`;
}

function ok(text: string) {
  return { content: [{ type: "text" as const, text }] };
}

// 발신자 telegram id(헤더)로 고정된 도구 세트를 가진 MCP 서버를 요청마다 생성(stateless)
function buildServer(tg: string): McpServer {
  const server = new McpServer({ name: "family-os", version: "0.1.0" });
  const tgNum = Number(tg);

  server.registerTool(
    "ledger_add",
    {
      description: "가계부 거래 등록 (source=TELEGRAM 강제). EXPENSE=source만/INCOME=target만/TRANSFER=둘 다.",
      inputSchema: {
        transactionType: z.enum(["EXPENSE", "INCOME", "TRANSFER"]),
        amount: z.number().positive(),
        currency: z.string().optional(),
        sourceAccountId: z.number().optional(),
        targetAccountId: z.number().optional(),
        categoryId: z.number().optional(),
        visibility: z.enum(["PRIVATE", "PARENTS", "FAMILY"]),
        occurredAt: z.string().describe("ISO-8601 UTC"),
        memo: z.string().optional(),
      },
    },
    async (args) =>
      ok(await callApi("POST", "/integrations/telegram/transactions", {
        body: { telegramUserId: tgNum, transaction: args },
      })),
  );

  server.registerTool(
    "ledger_stats",
    {
      description: "가계부 통계 조회 (TRANSFER 제외, 서버 집계).",
      inputSchema: {
        from: z.string().optional().describe("ISO-8601 UTC"),
        to: z.string().optional().describe("ISO-8601 UTC"),
        scope: z.string().optional(),
      },
    },
    async (a) =>
      ok(await callApi("GET", "/integrations/telegram/query", {
        query: { telegramUserId: tg, ...a },
      })),
  );

  server.registerTool(
    "timeline_get",
    {
      description: "통합 타임라인(지출+일정+기록) 조회.",
      inputSchema: {
        date: z.string().optional().describe("yyyy-MM-dd"),
        from: z.string().optional().describe("yyyy-MM-dd"),
        to: z.string().optional().describe("yyyy-MM-dd"),
      },
    },
    async (a) =>
      ok(await callApi("GET", "/integrations/telegram/timeline", {
        query: { telegramUserId: tg, ...a },
      })),
  );

  server.registerTool(
    "schedule_list",
    {
      description: "일정 조회.",
      inputSchema: {
        from: z.string().optional().describe("ISO-8601 UTC"),
        to: z.string().optional().describe("ISO-8601 UTC"),
        type: z.string().optional(),
      },
    },
    async (a) =>
      ok(await callApi("GET", "/integrations/telegram/schedules", {
        query: { telegramUserId: tg, ...a },
      })),
  );

  // ── 일정 쓰기: 백엔드 텔레그램 일정 엔드포인트(Phase 3, 구현됨) 호출 ──
  // ScheduleRequest 와 1:1. 이원화: allDay=true → startDate(필수)/endDate, started/ended 금지.
  //                              allDay=false → startedAt(필수)/endedAt, date 금지.
  const scheduleShape = {
    title: z.string(),
    description: z.string().optional(),
    location: z.string().optional(),
    startedAt: z.string().optional().describe("시점일정 시작(ISO-8601 UTC)"),
    endedAt: z.string().optional().describe("시점일정 종료(ISO-8601 UTC)"),
    startDate: z.string().optional().describe("종일일정 시작(yyyy-MM-dd)"),
    endDate: z.string().optional().describe("종일일정 종료(yyyy-MM-dd)"),
    allDay: z.boolean(),
    visibility: z.enum(["PRIVATE", "SHARED_PERSONAL", "PARENTS", "FAMILY"]),
    scheduleType: z.enum(["EVENT", "TODO", "REMINDER"]),
    recurrenceRule: z.string().optional(),
    collectionId: z.number().optional(),
    subjectPersonIds: z.array(z.number()).optional(),
    participantPersonIds: z.array(z.number()).optional(),
  };

  server.registerTool(
    "schedule_create",
    { description: "일정 등록 (백엔드 텔레그램 일정 쓰기 엔드포인트 필요).", inputSchema: scheduleShape },
    async (a) =>
      ok(await callApi("POST", "/integrations/telegram/schedules", {
        body: { telegramUserId: tgNum, schedule: a },
      })),
  );

  server.registerTool(
    "schedule_update",
    { description: "일정 수정.", inputSchema: { id: z.number(), ...scheduleShape } },
    async ({ id, ...rest }) =>
      ok(await callApi("PUT", `/integrations/telegram/schedules/${id}`, {
        body: { telegramUserId: tgNum, schedule: rest },
      })),
  );

  server.registerTool(
    "schedule_delete",
    { description: "일정 삭제(soft).", inputSchema: { id: z.number() } },
    async ({ id }) =>
      ok(await callApi("DELETE", `/integrations/telegram/schedules/${id}`, {
        body: { telegramUserId: tgNum },
      })),
  );

  return server;
}

const app = express();
app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ status: "UP", service: "family-os-mcp" });
});

app.post("/mcp", async (req, res) => {
  const tg = req.header("X-Telegram-User-Id");
  if (!tg) {
    res.status(401).json({
      jsonrpc: "2.0",
      id: null,
      error: { code: -32001, message: "X-Telegram-User-Id header required" },
    });
    return;
  }
  try {
    const server = buildServer(tg);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined }); // stateless
    res.on("close", () => {
      transport.close();
      server.close();
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (e) {
    console.error("[family-os-mcp] error", e);
    if (!res.headersSent) {
      res.status(500).json({ jsonrpc: "2.0", id: null, error: { code: -32603, message: String(e) } });
    }
  }
});

app.listen(PORT, () => {
  console.log(`[family-os-mcp] listening on :${PORT} → ${API_BASE}`);
});
