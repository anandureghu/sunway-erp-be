#!/usr/bin/env node
import { select } from "@inquirer/prompts";
import { loadEnv, getDbConfig } from "./lib/env.js";
import { createPool } from "./lib/db.js";
import { createBlobService } from "./lib/azure.js";
import clearInvoices from "./ops/clear-invoices.js";

loadEnv();

/** Register new ops here as the toolkit grows. */
const OPERATIONS = [clearInvoices];

async function main() {
  console.log("\nSunway backend cmd — admin maintenance tools");
  console.log("Destructive ops target the DB/storage from backend/.env (or cmd/.env).\n");

  const { pool, config: dbConfig } = await createPool();
  let blobService;
  try {
    blobService = createBlobService();
  } catch (err) {
    console.warn(`Azure not configured yet: ${err.message}`);
    blobService = null;
  }

  const ctx = { pool, dbConfig, blobService };

  try {
    for (;;) {
      const choice = await select({
        message: "Select an operation",
        choices: [
          ...OPERATIONS.map((op) => ({
            name: op.label,
            value: op.id,
          })),
          { name: "Quit", value: "__quit__" },
        ],
      });

      if (choice === "__quit__") {
        console.log("Bye.");
        break;
      }

      const op = OPERATIONS.find((o) => o.id === choice);
      if (!op) {
        console.error(`Unknown operation: ${choice}`);
        continue;
      }

      if (!blobService && choice === "clear-invoices") {
        try {
          blobService = createBlobService();
          ctx.blobService = blobService;
        } catch (err) {
          console.error(`Cannot run ${op.id}: ${err.message}`);
          continue;
        }
      }

      try {
        await op.run(ctx);
      } catch (err) {
        console.error(`\nOperation failed: ${err.message || err}`);
      }
      console.log("");
    }
  } finally {
    await pool.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
