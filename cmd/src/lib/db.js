import mysql from "mysql2/promise";
import { getDbConfig } from "./env.js";

export async function createPool() {
  const cfg = getDbConfig();
  const pool = mysql.createPool({
    host: cfg.host,
    port: cfg.port,
    database: cfg.database,
    user: cfg.user,
    password: cfg.password,
    waitForConnections: true,
    connectionLimit: 5,
    namedPlaceholders: true,
  });
  return { pool, config: cfg };
}

export async function withTransaction(pool, fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}
