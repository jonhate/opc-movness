import { z } from "zod";

export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const Artifact = z.object({
  id: z.string().regex(/^[a-f0-9]{64}$/), name: z.string(), bytes: z.number().int().nonnegative(),
  sha256: z.string().regex(/^[a-f0-9]{64}$/), phonePath: z.string(), mime: z.string(),
  sessionId: z.string(), sourcePath: z.string(), sourceVersion: z.string(), receivedAt: z.number(),
});
export type Artifact = z.infer<typeof Artifact>;
export interface FileSave {
  name: string; dataBase64: string; expectedBytes: number | null;
  sessionId: string; sourcePath: string; sourceVersion: string; receivedAt: number;
}
export interface ResultFiles {
  save(input: FileSave): Promise<Artifact>;
  list(): Promise<Artifact[]>;
}
