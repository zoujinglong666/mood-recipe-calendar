# Moment Records Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support editable one-to-nine-photo meal records with Moments-style timeline presentation.

**Architecture:** Preserve the current single cover URL for backward compatibility and add a JSON photo array. Reuse the existing record form for create/edit and add a detail route that owns display, edit, and image preview.

**Tech Stack:** Spring Boot/JPA, MySQL, Vue 3/uni-app, JUnit 5.

### Task 1: Add multi-photo record API

- [x] Add `image_urls` to schema/entity and migrate legacy `image_url` reads into a one-item list.
- [x] Extend record request/response with `imageUrls`, add authenticated `PUT /api/records/{id}`, and reject ownership violations or lists outside 1–9 images.
- [x] Add controller tests for legacy compatibility, update, and ownership.

### Task 2: Add record detail and edit flow

- [x] Add `record-detail` route and API fetch-by-id call.
- [x] Reuse the record form in create/edit mode; allow nine uploads, remove/reorder images, and save through POST or PUT.
- [x] Add image preview and an explicit edit action.

### Task 3: Render Moments-style photo grids

- [x] Update timeline cards and record detail to select single, two, compact four, or nine-grid class from image count.
- [x] Keep old one-image records visually unchanged and run type checking without rebuilding dirty dist artifacts.
