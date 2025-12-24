# Hammer Editor Web Design System

This document describes the design system used for Hammer Editor's server-side web interface. The design follows a "
Writer's Desk" aesthetic with a unified component library using BEM naming conventions.

## Table of Contents

- [Design Philosophy](#design-philosophy)
- [CSS Architecture](#css-architecture)
- [Design Tokens](#design-tokens)
- [Typography](#typography)
- [Shared Animations](#shared-animations)
- [Layout Components](#layout-components)
- [UI Components](#ui-components)
- [Form Components](#form-components)
- [Utility Classes](#utility-classes)
- [Responsive Design](#responsive-design)
- [Accessibility](#accessibility)

---

## Design Philosophy

The Hammer web interface uses a **"Writer's Desk"** aesthetic that evokes:

- **Notebook paper** with lined backgrounds and red margin lines
- **Typewriter typography** for headings and labels
- **Warm, paper-like colors** with amber/gold accents
- **Sticky note aesthetics** for feature cards
- **Subtle shadows and textures** that feel tactile

The goal is to create an interface that feels familiar and comfortable to writers, while remaining modern and
functional.

---

## CSS Architecture

### File Structure

```
server/src/main/resources/assets/css/
├── base.css           # Design tokens, typography, shared components
├── home.css           # Landing/marketing page
├── dashboard.css      # User dashboard
├── admin.css          # Admin pages
├── login.css          # Login page
├── author.css         # Public author pages
├── story.css          # Story viewing/editing pages
└── error.css          # Error pages (404, 500, etc.)
```

### Naming Convention

We use **BEM (Block Element Modifier)** naming:

```css
.block {
}

/* Component block */
.block__element {
}

/* Child element */
.block--modifier {
}

/* Variant/state */
```

Examples:

- `.card` - Block (the component)
- `.card__title` - Element (part of the card)
- `.card--interactive` - Modifier (makes it clickable)

---

## Design Tokens

### Color Palette

```css
/* Primary Colors */
--primary: #2563eb

; /* Blue - links, primary actions */
--primary-dark: #1e40af

;
--primary-light: #3b82f6

;

/* Accent Colors (Amber/Gold) */
--accent: #d97706

; /* Main accent - borders, highlights */
--accent-light: #f59e0b

;
--accent-dark: #b45309

;

/* Neutral Scale (Stone) */
--neutral-50: #fafaf9

;
--neutral-100: #f5f5f4

;
--neutral-200: #e7e5e4

;
--neutral-300: #d6d3d1

;
--neutral-400: #a8a29e

;
--neutral-500: #78716c

;
--neutral-600: #57534e

;
--neutral-700: #44403c

;
--neutral-800: #292524

;
--neutral-900: #1c1917

;

/* Writer's Desk Colors */
--sticky-yellow: #fef08a

; /* Yellow sticky notes */
--sticky-blue: #bfdbfe

; /* Blue sticky notes */
--sticky-green: #bbf7d0

; /* Green sticky notes */
--sticky-pink: #fbcfe8

; /* Pink sticky notes */
--paper-white: #fffef9

; /* Warm paper color */
--ink: #1c1917

; /* Dark ink color */

/* Semantic Colors */
--success: #10b981

; /* Green - success states */
--warning: #f59e0b

; /* Amber - warnings */
--error: #ef4444

; /* Red - errors */
```

### Shadows

```css
--shadow-sm:

0
1
px

2
px

0
rgba
(
0
,
0
,
0
,
0.05
)
;
--shadow-md:

0
4
px

6
px

-
1
px

rgba
(
0
,
0
,
0
,
0.1
)
,
0
2
px

4
px

-
1
px

rgba
(
0
,
0
,
0
,
0.06
)
;
--shadow-lg:

0
10
px

15
px

-
3
px

rgba
(
0
,
0
,
0
,
0.1
)
,
0
4
px

6
px

-
2
px

rgba
(
0
,
0
,
0
,
0.05
)
;
--shadow-xl:

0
20
px

25
px

-
5
px

rgba
(
0
,
0
,
0
,
0.1
)
,
0
10
px

10
px

-
5
px

rgba
(
0
,
0
,
0
,
0.04
)
;
--shadow-sticky:

0
1
px

3
px

rgba
(
0
,
0
,
0
,
0.12
)
,
0
1
px

2
px

rgba
(
0
,
0
,
0
,
0.24
)
;
--shadow-sticky-hover:

0
10
px

20
px

rgba
(
0
,
0
,
0
,
0.15
)
,
0
3
px

6
px

rgba
(
0
,
0
,
0
,
0.10
)
;
```

### Animation Timing

```css
--transition-fast:

150
ms

cubic-bezier
(
0.4
,
0
,
0.2
,
1
)
;
--transition-base:

250
ms

cubic-bezier
(
0.4
,
0
,
0.2
,
1
)
;
--transition-slow:

350
ms

cubic-bezier
(
0.4
,
0
,
0.2
,
1
)
;
```

---

## Typography

### Font Families

1. **Kingthings Typewriter** - Headings, labels, buttons
	- Evokes vintage typewriter aesthetic
	- Used for all `h1-h6`, form labels, button text

2. **Lora** - Body text, paragraphs
	- Elegant serif font for readability
	- Used for body copy, descriptions, input values

### Usage

```css
/* Headings and UI elements */
font-family:

'Kingthings Typewriter'
,
monospace

;

/* Body text */
font-family:

'Lora'
,
Georgia, serif

;
```

### Responsive Font Sizes

```css
h1 {
	font-size: clamp(2.5rem, 5vw, 4.5rem);
}

h2 {
	font-size: clamp(2rem, 4vw, 3.5rem);
}

h3 {
	font-size: clamp(1.5rem, 3vw, 2.25rem);
}
```

---

## Shared Animations

These animations are defined in `base.css` and can be used across all pages:

```css
/* Fade effects */
@keyframes fadeIn {
	from {
		opacity: 0;
	}
	to {
		opacity: 1;
	}
}

@keyframes fadeOut {
	from {
		opacity: 1;
	}
	to {
		opacity: 0;
	}
}

/* Slide effects */
@keyframes slideInUp {
	from {
		opacity: 0;
		transform: translateY(20px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

@keyframes slideInDown {
	from {
		opacity: 0;
		transform: translateY(-20px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}
```

Usage:

```css
.my-element {
	animation: slideInUp 0.5s ease-out;
}
```

---

## Layout Components

### Page Container

The base wrapper for page content.

```html

<main class="page">
	<div class="page__container">
		<!-- Page content -->
	</div>
</main>
```

**Modifiers:**

- `.page--centered` - Centers content vertically and horizontally
- `.page__container--narrow` - Max-width 480px (forms, login)
- `.page__container--wide` - Max-width 1200px (admin, dashboards)

**Page-specific modifiers:**

- `.page__container--author` - Max-width 900px
- `.page__container--error` - Max-width 600px

### Page Header

Dark header bar with title, subtitle, and optional actions.

```html

<div class="page-header">
	<div class="page-header__content">
		<h1 class="page-header__title">Dashboard</h1>
		<p class="page-header__subtitle">Welcome back, username</p>
	</div>
	<div class="page-header__actions">
		<button class="btn btn--ghost">Settings</button>
	</div>
</div>
```

### Paper Section

Notebook-style content sections with lined background and red margin.

```html

<div class="paper-section">
	<div class="section-header">
		<h2 class="section-header__title">Your Projects</h2>
		<div class="section-header__actions">
			<button class="btn">New Project</button>
		</div>
	</div>
	<p class="section-description">Manage your writing projects.</p>
	<!-- Section content -->
</div>
```

**Modifiers:**

- `.paper-section--compact` - Reduced padding
- `.paper-section--centered` - Center-aligned text

---

## UI Components

### Cards

Interactive card components for lists of items.

```html

<div class="card-list">
	<a href="/project/1" class="card card--interactive">
		<div class="card__info">
			<h3 class="card__title">My Novel</h3>
			<span class="card__subtitle">Last synced: 2 hours ago</span>
		</div>
		<i class="fa-solid fa-chevron-right card__arrow"></i>
	</a>
</div>
```

**Modifiers:**

- `.card--interactive` - Adds hover effects (translateX, border change)
- `.card--gradient` - Gradient background

### Dialog/Modal

Modal dialogs with header, body, and actions.

```html

<div class="dialog-overlay" id="my-dialog">
	<div class="dialog">
		<div class="dialog__header">
			<h3>Dialog Title</h3>
			<button class="dialog__close">
				<i class="fas fa-times"></i>
			</button>
		</div>
		<div class="dialog__body">
			<!-- Dialog content -->
		</div>
		<div class="dialog__actions">
			<button class="btn btn--ghost">Cancel</button>
			<button class="btn btn--accent">Confirm</button>
		</div>
	</div>
</div>
```

**Modifiers:**

- `.dialog--warning` - Warning variant with amber tint

**JavaScript:**
Add `.closing` class to `.dialog-overlay` to trigger fade-out animation.

### Buttons

```html
<!-- Default (outlined) -->
<button class="btn">Default</button>

<!-- Primary (filled dark, hover to accent) -->
<button class="btn btn--primary">Primary</button>

<!-- Accent (filled amber) -->
<button class="btn btn--accent">Accent</button>

<!-- Danger (red outlined/filled) -->
<button class="btn btn--danger">Delete</button>

<!-- Ghost (subtle) -->
<button class="btn btn--ghost">Cancel</button>

<!-- Full width -->
<button class="btn btn--primary btn--block">Submit</button>
```

### Pagination

```html

<div class="pagination">
	<button class="pagination-button">&laquo; Previous</button>
	<span class="pagination-info">Page 1 of 5</span>
	<button class="pagination-button">Next &raquo;</button>
</div>
```

### Empty State

For displaying when lists have no content.

```html

<div class="empty-state">
	<div class="empty-state__icon">📝</div>
	<p>No projects yet. Start writing!</p>
</div>
```

### Toast Notifications

```html

<div id="toast-container">
	<div class="toast toast-success">
		<span class="toast-icon">✓</span>
		<span class="toast-message">Changes saved successfully</span>
		<button class="toast-dismiss">×</button>
	</div>
</div>
```

**Variants:** `.toast-success`, `.toast-warning`, `.toast-error`, `.toast-info`

---

## Form Components

### Form Field

```html

<div class="form-field">
	<label for="email" class="form-field__label">Email Address</label>
	<input type="email" id="email" class="form-input" placeholder="you@example.com">
</div>
```

### Input Types

```html
<!-- Text Input -->
<input type="text" class="form-input">

<!-- Select Dropdown -->
<select class="form-select">
	<option>Option 1</option>
	<option>Option 2</option>
</select>

<!-- Textarea -->
<textarea class="form-textarea"></textarea>
```

### Form Field Label Variants

```html
<label class="form-field__label">Regular Label</label>
<label class="form-field__label form-field__label--small">Small Label</label>
```

---

## Utility Classes

```css
.hidden {
	display: none !important;
}
```

---

## Responsive Design

The design system includes responsive breakpoints:

| Breakpoint | Width          | Usage                 |
|------------|----------------|-----------------------|
| Large      | > 1024px       | Full desktop layout   |
| Medium     | 769px - 1024px | Tablet/narrow desktop |
| Small      | 601px - 768px  | Large mobile          |
| X-Small    | ≤ 600px        | Mobile phones         |
| XX-Small   | ≤ 480px        | Small phones          |

### Behavior Changes

**768px and below:**

- Page headers stack vertically
- Paper sections reduce padding
- Cards don't transform on hover
- Dialogs use 95% width

**480px and below:**

- Smaller font sizes for titles
- Reduced spacing throughout

---

## Accessibility

### Reduced Motion

All animations respect `prefers-reduced-motion`:

```css
@media (prefers-reduced-motion: reduce) {
	*, *::before, *::after {
		animation-duration: 0.01ms !important;
		transition-duration: 0.01ms !important;
	}
}
```

### Focus States

All interactive elements have visible focus states:

```css
*:focus-visible {
	outline: 2px solid var(--accent);
	outline-offset: 3px;
}
```

### Color Contrast

- Text colors meet WCAG AA contrast requirements
- Interactive elements have clear hover/focus states
- Error states use both color and icons for accessibility

---

## Page-Specific Extensions

While the shared components cover most use cases, some pages have additional specific styles:

- **home.css** - Marketing page sticky notes, feature grid, download cards
- **dashboard.css** - Pen name section, danger zone, account info
- **admin.css** - Sidebar navigation, whitelist management
- **login.css** - Login header styling, error shake animation, whitelist notices
- **author.css** - Author header with avatar, story count
- **story.css** - Story content formatting, publish toggle, access list
- **error.css** - Colored sticky-note error cards with animations

---

## Example: Complete Page Structure

```html
{{> header}}

<main class="page">
	<div class="page__container">
		<!-- Dark header bar -->
		<div class="page-header">
			<div class="page-header__content">
				<h1 class="page-header__title">My Page</h1>
				<p class="page-header__subtitle">Subtitle text</p>
			</div>
		</div>

		<!-- Content section -->
		<div class="paper-section">
			<div class="section-header">
				<h2 class="section-header__title">Section Title</h2>
			</div>

			<!-- Card list -->
			<div class="card-list">
				<a href="#" class="card card--interactive">
					<div class="card__info">
						<h3 class="card__title">Item Title</h3>
						<span class="card__subtitle">Subtitle</span>
					</div>
					<i class="fa-solid fa-chevron-right card__arrow"></i>
				</a>
			</div>

			<!-- Or empty state -->
			<div class="empty-state">
				<p>No items yet.</p>
			</div>
		</div>
	</div>
</main>

{{> footer}}
```
