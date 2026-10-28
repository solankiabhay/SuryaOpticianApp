# Surya Optician image assets

Keep downloaded/approved visual assets in this folder, separate from app source code.

## Naming convention
- `product-frame-<brand>-<model>.webp` — eyewear product photos
- `product-lens-<type>.webp` — lens photos
- `shop-banner.webp` — optional dashboard/banner image
- `placeholder-product.svg` — locally-created placeholder when no real product photo is available

## Adding downloaded images
1. Download only images you own or have permission to use.
2. Prefer WebP or optimized JPEG/PNG; use descriptive filenames.
3. Keep the original source URL and licence/permission in `ASSET_SOURCES.md`.
4. Android runtime resources should be copied into `app/src/main/res/drawable-nodpi/` with lowercase filenames. Keep the source copy here for easy replacement.

Do not use random hotlinked images in production. Real product photos should be associated with the correct product record, not shown as if they depict a different product.
