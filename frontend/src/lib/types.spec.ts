import { describe, expect, it } from 'vitest';
import type { ErrorKind, ProgressEvent, ReadingDirection, SubmitResponse } from './types';

const JOB_ID_PATTERN = /^[0-9a-f-]{36}$/;
const ERROR_KIND_PATTERN = /^[A-Z_]+$/;

/**
 * Pins the runtime shape of frames coming back from /ws/jobs/{id} against the
 * generated TypeScript discriminated union. The Java side regenerates types.ts
 * on every build (verifyApiTypesUpToDate); this test is the consumer-side end of
 * the same contract — if the SvelteKit code starts narrowing on a property
 * Jackson no longer emits, this fails loud.
 *
 * These are pure type-guard tests; no DOM, no Svelte runtime, no @testing-library
 * needed — keeps the unit test surface light until the next PR adds the
 * component-level tests.
 */
describe('ProgressEvent wire shape', () => {
	function parse(json: string): ProgressEvent {
		// The frontend uses exactly this cast in +page.svelte; mirror it here so a
		// drift between Java emitter and TS consumer surfaces as a test failure
		// rather than a runtime narrowing bug in production.
		return JSON.parse(json) as ProgressEvent;
	}

	it('narrows started frames by the discriminator', () => {
		const ev = parse('{"type":"started","total":12,"traceId":"t-1"}');
		expect(ev.type).toBe('started');
		if (ev.type === 'started') {
			expect(ev.total).toBe(12);
			expect(ev.traceId).toBe('t-1');
		}
	});

	it('narrows progress frames and exposes current + total', () => {
		const ev = parse('{"type":"progress","current":3,"total":12,"traceId":"t-1"}');
		if (ev.type === 'progress') {
			expect(ev.current).toBe(3);
			expect(ev.total).toBe(12);
		} else {
			expect.fail(`expected progress, got ${ev.type}`);
		}
	});

	it('narrows completed frames without further fields beyond traceId', () => {
		const ev = parse('{"type":"completed","traceId":"t-1"}');
		expect(ev.type).toBe('completed');
		expect(ev.traceId).toBe('t-1');
	});

	it('narrows failed frames and exposes errorKind as a literal', () => {
		const ev = parse(
			'{"type":"failed","errorKind":"PDF_PASSWORD_PROTECTED","message":"hi","traceId":"t-1"}'
		);
		if (ev.type === 'failed') {
			expect(ev.errorKind).toBe<ErrorKind>('PDF_PASSWORD_PROTECTED');
			expect(ev.message).toBe('hi');
		} else {
			expect.fail(`expected failed, got ${ev.type}`);
		}
	});

	it('SubmitResponse round-trips the job id string', () => {
		const parsed = JSON.parse('{"id":"00000000-0000-0000-0000-000000000000"}') as SubmitResponse;
		expect(parsed.id).toMatch(JOB_ID_PATTERN);
	});

	it('ReadingDirection union accepts only RTL and LTR', () => {
		const direction: ReadingDirection = 'RTL';
		expect(direction).toBe('RTL');
		// @ts-expect-error — unknown direction must be rejected by the compiler
		const bogus: ReadingDirection = 'DOWN';
		expect(bogus).toBe('DOWN'); // runtime side-effect only; the compile-time fence is what matters
	});

	it('every documented ErrorKind is structurally a string literal', () => {
		// Sample a few representative kinds (each one drawn from the generated union).
		const kinds: ErrorKind[] = ['PDF_TOO_LARGE', 'UPLOAD_EMPTY', 'INTERNAL'];
		for (const k of kinds) {
			expect(typeof k).toBe('string');
			expect(k).toMatch(ERROR_KIND_PATTERN);
		}
	});
});
