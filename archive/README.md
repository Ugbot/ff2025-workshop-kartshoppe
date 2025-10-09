# Archive - Old Startup Scripts

This directory contains previous startup scripts that have been superseded by the new training-oriented setup.

## Why These Scripts Are Archived

The original scripts were designed for general development/demo usage, but they had issues for training purposes:

1. **Automated Flink job startup** - `start-all.sh` automatically started Flink jobs, which isn't ideal for progressive learning
2. **Complex multi-step process** - Required managing multiple terminals manually
3. **Mixed concerns** - Combined platform startup with Flink job execution

## New Training-Oriented Scripts

The project now uses a cleaner, training-friendly approach:

### Setup & Platform
- `0-setup-environment.sh` - One-time environment setup (SDKMAN, Java 11 & 17)
- `start-training-platform.sh` - Start platform WITHOUT Flink jobs
- `stop-platform.sh` - Clean shutdown

### Flink Jobs (Run Separately)
- `flink-1-inventory-job.sh` - Module 1: Inventory management
- `flink-2-basket-analysis-job.sh` - Module 2: Recommendations
- `flink-3-hybrid-source-job.sh` - Module 3: Warm-start with history
- `flink-4-shopping-assistant-job.sh` - Module 4: AI assistant

## Archived Scripts

### `start-all.sh`
**Replaced by:** `start-training-platform.sh` (platform only)

**Issues:**
- Auto-started Flink inventory job (line 76-88)
- Mixed platform startup with Flink job execution
- Not suitable for step-by-step training

**When to use:** If you want everything running automatically for a demo (not training)

### `1-start-infrastructure.sh`
**Replaced by:** Integrated into `start-training-platform.sh` (Step 2)

### `2-start-quarkus.sh`
**Replaced by:** Integrated into `start-training-platform.sh` (Step 5)

### `3-start-frontend.sh`
**Replaced by:** No longer needed - Quinoa integrates frontend into Quarkus

### `start-platform.sh`
**Replaced by:** `start-training-platform.sh`

**Issues:**
- Attempted to open multiple terminals automatically (macOS-specific)
- Complex terminal management logic
- Not reliable across different environments

## Can I Still Use These?

Yes! These scripts still work, but they're not recommended for training sessions because:

1. They don't follow the progressive learning approach
2. They may auto-start Flink jobs before concepts are taught
3. They don't include the environment setup script

## Migration Guide

If you have existing documentation referencing these scripts:

| Old Script | New Equivalent |
|------------|----------------|
| `start-all.sh` | `start-training-platform.sh` + Flink job scripts |
| `1-start-infrastructure.sh` | Part of `start-training-platform.sh` |
| `2-start-quarkus.sh` | Part of `start-training-platform.sh` |
| `3-start-frontend.sh` | Removed (Quinoa handles this) |
| `start-platform.sh` | `start-training-platform.sh` |

## Restoring Old Behavior

If you need the old "start everything" behavior:

```bash
# Use the archived script
./archive/start-all.sh

# Or manually run:
./start-training-platform.sh
./flink-1-inventory-job.sh
./flink-2-basket-analysis-job.sh
```

---

**Last Updated:** 2025-10-08
**Archive Reason:** Training reorganization for better learning outcomes
