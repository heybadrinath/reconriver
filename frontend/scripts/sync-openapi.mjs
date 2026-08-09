import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const source = process.env.OPENAPI_URL ?? "http://localhost:8080/v3/api-docs";
const destination = new URL("../openapi/openapi.json", import.meta.url);
const response = await fetch(source, {
  headers: { Accept: "application/json" },
});

if (!response.ok) {
  throw new Error(
    `OpenAPI download failed with status ${response.status} from ${source}.`,
  );
}

const document = await response.json();
mkdirSync(dirname(fileURLToPath(destination)), { recursive: true });
writeFileSync(destination, `${JSON.stringify(document, null, 2)}\n`, "utf8");
process.stdout.write(`Updated openapi/openapi.json from ${source}.\n`);
