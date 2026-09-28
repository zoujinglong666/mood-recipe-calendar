# Agent reliability design

## Goal

Make Guozai converse naturally, only offering a selection card when it cannot confidently continue from free text, and make large weekly menus complete, readable, unique, and recipe-specific.

## Model routing

`agnes-3.0-flash` is the primary model for structured dialogue understanding, decision making, menu generation, and repair. `agnes-2.5-flash` is a fallback only after the primary model has a timeout, non-JSON response, or output that fails server validation. Both use the existing OpenAI-compatible Chat Completions endpoint and key. Audit data records the model actually used and why a fallback occurred.

## Conversation

The turn response carries an optional card. The backend sends it only when the understanding result has unresolved key information, a conflict needs a constrained choice, or the model cannot parse the user message. Normal understood replies remain text-only. Every option card includes an `其他` option; the client changes it into a focused free-text input and submits that text through the normal turn endpoint.

## Weekly menus

Menus with more than 12 dishes are generated in daily batches. Each later batch receives the already accepted dish names. The server preserves model-provided ingredients and steps, rejects malformed or replacement-character names, and validates global uniqueness before saving. A local fallback may use each candidate once only; if it cannot supply enough distinct dishes, generation fails explicitly instead of repeating a dish.

## Verification

Tests cover optional cards, `其他` free input, primary-to-fallback routing, retaining different model steps, rejecting malformed names, and a 7-by-7 menu having 49 distinct dishes or returning an explicit insufficient-candidate error.
