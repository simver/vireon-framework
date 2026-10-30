import { config } from "zod";

// Evaluate this module before Scalar creates schemas. Keep CSP free of unsafe-eval.
config({ jitless: true });
