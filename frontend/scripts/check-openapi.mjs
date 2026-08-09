import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const liveSource =
  process.env.OPENAPI_URL ?? "http://localhost:8080/v3/api-docs";
const directory = mkdtempSync(join(tmpdir(), "reconriver-openapi-"));
const generated = join(directory, "schema.d.ts");
const committed = new URL("../src/lib/api/schema.d.ts", import.meta.url);
const committedDocument = new URL("../openapi/openapi.json", import.meta.url);

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, child]) => [key, canonicalize(child)]),
    );
  }
  return value;
}

try {
  execFileSync(
    "pnpm",
    [
      "exec",
      "openapi-typescript",
      fileURLToPath(committedDocument),
      "-o",
      generated,
    ],
    {
      stdio: "inherit",
    },
  );
  const normalize = (value) => value.replaceAll("\r\n", "\n").trim();
  if (
    normalize(readFileSync(generated, "utf8")) !==
    normalize(readFileSync(committed, "utf8"))
  ) {
    process.stderr.write(
      "The committed TypeScript API contract is stale. Run pnpm api:generate.\n",
    );
    process.exitCode = 1;
  } else {
    process.stdout.write(
      "The generated TypeScript API contract matches the committed OpenAPI document.\n",
    );
  }

  if (process.argv.includes("--live")) {
    const response = await fetch(liveSource, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(
        `OpenAPI download failed with status ${response.status} from ${liveSource}.`,
      );
    }
    const liveDocument = await response.json();
    const expectedDocument = JSON.parse(
      readFileSync(committedDocument, "utf8"),
    );
    if (
      JSON.stringify(canonicalize(liveDocument)) !==
      JSON.stringify(canonicalize(expectedDocument))
    ) {
      process.stderr.write(
        "The committed OpenAPI document differs from the live Spring API. Run pnpm api:refresh.\n",
      );
      process.exitCode = 1;
    } else {
      process.stdout.write(
        "The committed OpenAPI document matches the live Spring API.\n",
      );
    }
  }
} finally {
  rmSync(directory, { recursive: true, force: true });
}
