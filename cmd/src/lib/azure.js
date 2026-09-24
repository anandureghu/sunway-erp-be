import { BlobServiceClient } from "@azure/storage-blob";
import { getAzureConfig } from "./env.js";

export function createBlobService() {
  const cfg = getAzureConfig();
  const client = BlobServiceClient.fromConnectionString(cfg.connectionString);
  return {
    client,
    publicContainer: cfg.publicContainer,
    privateContainer: cfg.privateContainer,
  };
}

/**
 * Resolve blob path from a full Azure blob URL (with or without SAS query).
 * Mirrors FileStorageService.extractBlobPathFromUrl.
 */
export function extractBlobPathFromUrl(fileUrl, publicContainer, privateContainer) {
  if (!fileUrl || !String(fileUrl).trim()) return null;
  const clean = String(fileUrl).trim().split("?", 2)[0];
  for (const containerName of [publicContainer, privateContainer]) {
    if (!containerName) continue;
    const marker = `/${containerName}/`;
    const idx = clean.indexOf(marker);
    if (idx >= 0) {
      return clean.substring(idx + marker.length);
    }
  }
  return null;
}

/**
 * Best-effort delete: try public then private container; remove stored_files row via callback.
 * @returns {{ deleted: boolean, blobPath: string|null, error?: string }}
 */
export async function deleteByPublicUrl(blobService, fileUrl, onLedgerDelete) {
  const blobPath = extractBlobPathFromUrl(
    fileUrl,
    blobService.publicContainer,
    blobService.privateContainer
  );
  if (!blobPath) {
    return { deleted: false, blobPath: null };
  }
  return deleteByBlobPath(blobService, blobPath, onLedgerDelete);
}

export async function deleteByBlobPath(blobService, blobPath, onLedgerDelete) {
  if (!blobPath) {
    return { deleted: false, blobPath: null };
  }
  let deleted = false;
  let lastError;
  for (const containerName of [
    blobService.publicContainer,
    blobService.privateContainer,
  ]) {
    try {
      const container = blobService.client.getContainerClient(containerName);
      const blob = container.getBlobClient(blobPath);
      if (await blob.exists()) {
        await blob.delete();
        deleted = true;
      }
    } catch (err) {
      lastError = err?.message || String(err);
    }
  }
  if (typeof onLedgerDelete === "function") {
    try {
      await onLedgerDelete(blobPath);
    } catch (err) {
      lastError = err?.message || String(err);
    }
  }
  return {
    deleted,
    blobPath,
    ...(lastError && !deleted ? { error: lastError } : {}),
  };
}
