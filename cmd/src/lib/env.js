import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const cmdRoot = path.resolve(__dirname, "../..");
const backendRoot = path.resolve(cmdRoot, "..");

/**
 * Load backend/.env then cmd/.env (cmd wins for overrides).
 */
export function loadEnv() {
  dotenv.config({ path: path.join(backendRoot, ".env") });
  dotenv.config({ path: path.join(cmdRoot, ".env"), override: true });
}

export function getDbConfig() {
  return {
    host: process.env.DB_HOST || "localhost",
    port: Number(process.env.DB_PORT || 3306),
    database: process.env.DB_NAME || "hrdb",
    user: process.env.DB_USER || "root",
    password: process.env.DB_PASS ?? "",
  };
}

export function getAzureConfig() {
  const connectionString = process.env.AZURE_STORAGE_CONNECTION_STRING;
  if (!connectionString) {
    throw new Error("AZURE_STORAGE_CONNECTION_STRING is not set (check backend/.env)");
  }
  return {
    connectionString,
    publicContainer: process.env.AZURE_PUBLIC_CONTAINER || "erp-public",
    privateContainer: process.env.AZURE_PRIVATE_CONTAINER || "erp-private",
  };
}

/** Local-looking hosts may run destructive ops without ALLOW_DESTRUCTIVE_OPS. */
export function assertDestructiveAllowed(dbHost) {
  const host = (dbHost || "").toLowerCase();
  const local =
    host === "localhost" ||
    host === "127.0.0.1" ||
    host === "::1" ||
    host.endsWith(".local");
  if (local) return;
  if (process.env.ALLOW_DESTRUCTIVE_OPS === "1") return;
  throw new Error(
    `Refusing destructive ops against DB_HOST=${dbHost}. ` +
      `Set ALLOW_DESTRUCTIVE_OPS=1 to override (use with care).`
  );
}

export { cmdRoot, backendRoot };
