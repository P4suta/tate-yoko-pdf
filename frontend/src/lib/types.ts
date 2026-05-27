export type ProgressEvent =
	| { type: 'started'; total: number; traceId: string }
	| { type: 'progress'; current: number; total: number; traceId: string }
	| { type: 'completed'; traceId: string }
	| { type: 'failed'; errorKind: string; message: string; traceId: string };

export type SubmitResponse = { id: string };

export type ReadingDirection = 'RTL' | 'LTR';
