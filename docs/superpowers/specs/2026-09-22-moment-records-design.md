# Moment records design

## Goal

Allow a meal record to contain up to nine photos, be edited after publishing, and render in a Moments-style timeline grid.

## Data and compatibility

Add `image_urls` as a JSON array to `user_records` while retaining `image_url` as the first image for legacy clients and album features. Responses expose both `imageUrl` and `imageUrls`. Create and update validate one to nine URLs, and old records are read as a one-item array.

## Pages

The record form selects and uploads multiple images concurrently, supports deletion and reordering, and uses the first retained image as cover. A record-detail page renders the meal, nine-grid photos, date, mood, note, and an edit action. The edit form reuses the record page in edit mode.

## Timeline layout

One image is a wide featured image; two images use a two-column row; three or four images use a compact two-column grid; five to nine images use a three-column square grid. Tapping an image opens native preview at that index.

## Verification

Backend tests cover create, update ownership, legacy image fallback, and nine-image validation. Frontend type checking verifies the new route and optional arrays.
