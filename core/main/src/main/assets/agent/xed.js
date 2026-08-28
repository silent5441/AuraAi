import { tool } from "@opencode-ai/plugin"

const DEFAULT_PORT = 9270
const PORT = Number(process.env.XED_BRIDGE_PORT || DEFAULT_PORT)
const BASE = `http://127.0.0.1:${PORT}`
const TIMEOUT_MS = 15000

const BRIDGE_DOWN_HINT = `The Agent Bridge is not reachable at ${BASE}. Tell the user to: 1) open the Xed/AuraAi app, 2) open Settings → Agent, 3) make sure the "Agent bridge" switch is ON and the app is running (foreground or in a terminal session), then retry. The bridge starts automatically whenever a terminal session is open, so opening the terminal usually fixes this.`

async function callBridge(path, body) {
  const res = await fetch(BASE + path, {
    method: body === undefined ? "GET" : "POST",
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`bridge error ${res.status}: ${text}`)
  return text
}

async function guarded(path, body) {
  try {
    return await callBridge(path, body)
  } catch (e) {
    const msg = String((e && e.message) || e)
    const bridgeDown = /fetch failed|ECONNREFUSED|ECONNRESET|ETIMEDOUT|timeout|aborted|unreachable|network/i.test(msg)
    return bridgeDown ? `Bridge unreachable at ${BASE} (${msg}). ${BRIDGE_DOWN_HINT}` : `tool error: ${msg}`
  }
}

export const volume = tool({
  description:
    "Control the phone's media volume. action 'get' returns the current level, 'set' sets an absolute level (0-100, requires level), 'step' changes it by a relative amount (requires step), 'mute' toggles mute (requires mute).",
  args: {
    action: tool.schema
      .string()
      .optional()
      .describe('"get" (default), "set", "step" or "mute"'),
    level: tool.schema.number().optional().describe("Absolute volume level 0-100 for action 'set'"),
    step: tool.schema.number().optional().describe("Relative volume delta for action 'step'"),
    mute: tool.schema.boolean().optional().describe("Whether to mute or unmute for action 'mute'"),
  },
  async execute(args) {
    if (!args.action || args.action === "get") {
      return await guarded("/volume")
    }
    const body = {}
    if (args.level !== undefined) body.level = args.level
    if (args.step !== undefined) body.step = args.step
    if (args.mute !== undefined) body.mute = args.mute
    return await guarded("/volume", body)
  },
})

export const media = tool({
  description: "Control phone media playback: play, pause, toggle, next, previous or stop.",
  args: {
    action: tool.schema.string().describe('"play", "pause", "toggle", "next", "previous" or "stop"'),
  },
  async execute(args) {
    return await guarded("/media", { action: args.action })
  },
})

export const download = tool({
  description:
    "Download a URL on the phone using yt-dlp. Returns a job id; poll with xed_download_status until it finishes. Videos land in ~/Downloads inside the sandbox.",
  args: {
    url: tool.schema.string().describe("The URL to download"),
    format: tool.schema
      .string()
      .optional()
      .describe('"video" (default) or "audio"'),
  },
  async execute(args) {
    return await guarded("/download", { url: args.url, format: args.format || "video" })
  },
})

export const download_status = tool({
  description:
    "Check the status of a download job started with xed_download. Without an id, lists all download jobs.",
  args: {
    id: tool.schema.string().optional().describe("Job id returned by xed_download"),
  },
  async execute(args) {
    if (args.id) {
      return await guarded(`/download?id=${encodeURIComponent(args.id)}`)
    }
    return await guarded("/downloads")
  },
})

export const open = tool({
  description:
    "Open a URL in the phone's browser or launch an installed app by its Android package name (e.g. com.spotify.music).",
  args: {
    url: tool.schema.string().optional().describe("URL to open in the browser"),
    package: tool.schema.string().optional().describe("Android package name of an app to launch"),
  },
  async execute(args) {
    return await guarded("/open", args)
  },
})

export const clipboard = tool({
  description: "Read or write the phone's clipboard. Action 'get' returns the current text.",
  args: {
    action: tool.schema.string().describe('"get" or "set"'),
    text: tool.schema.string().optional().describe("Text to write for action 'set'"),
  },
  async execute(args) {
    if (args.action === "get") {
      return await guarded("/clipboard")
    }
    return await guarded("/clipboard", { text: args.text || "" })
  },
})

export const notify = tool({
  description: "Show a notification on the phone screen.",
  args: {
    title: tool.schema.string().describe("Notification title"),
    text: tool.schema.string().describe("Notification body text"),
  },
  async execute(args) {
    return await guarded("/notify", { title: args.title, text: args.text })
  },
})

export const device = tool({
  description: "Get information about the phone: model, Android version, battery, etc.",
  args: {},
  async execute() {
    return await guarded("/device")
  },
})

export const shell = tool({
  description:
    "Run an arbitrary shell command inside the Ubuntu sandbox on the phone with full sandbox access (apt, npm, pip, file system, network all available). Use for anything not covered by other tools.",
  args: {
    command: tool.schema.string().describe("The shell command to run"),
    timeout: tool.schema.number().optional().describe("Timeout in seconds"),
  },
  async execute(args) {
    const body = { command: args.command }
    if (args.timeout !== undefined) body.timeout = args.timeout
    return await guarded("/shell", body)
  },
})

export const files = tool({
  description: "List files in a directory inside the Ubuntu sandbox (default: home directory).",
  args: {
    path: tool.schema.string().optional().describe('Directory to list, "~" by default'),
  },
  async execute(args) {
    return await guarded(`/files/list?path=${encodeURIComponent(args.path || "~")}`)
  },
})

export const files_read = tool({
  description: "Read a text file inside the Ubuntu sandbox.",
  args: {
    path: tool.schema.string().describe("Path of the file to read"),
    limit: tool.schema.number().optional().describe("Maximum number of lines to return"),
  },
  async execute(args) {
    const query = `path=${encodeURIComponent(args.path)}`
    const limit = args.limit !== undefined ? `&limit=${encodeURIComponent(args.limit)}` : ""
    return await guarded(`/files/read?${query}${limit}`)
  },
})

export const files_to_shared = tool({
  description:
    "Copy a file from inside the Ubuntu sandbox to the phone's public Downloads folder so it is accessible outside the app.",
  args: {
    path: tool.schema.string().describe("Path of the file in the sandbox to copy out"),
  },
  async execute(args) {
    return await guarded("/files/to-shared", { path: args.path })
  },
})

export const health = tool({
  description: "Check whether the phone Agent Bridge is reachable. Use this first when xed_* tools fail.",
  args: {},
  async execute() {
    try {
      return await callBridge("/health")
    } catch (e) {
      const msg = String((e && e.message) || e)
      return `Bridge unreachable at ${BASE} (${msg}). ${BRIDGE_DOWN_HINT}`
    }
  },
})