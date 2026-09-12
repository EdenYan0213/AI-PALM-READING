---
name: Cyber-Ethereal Palmistry
colors:
  surface: '#fff8f7'
  surface-dim: '#dfd8d8'
  surface-bright: '#fff8f7'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f9f2f2'
  surface-container: '#f3ecec'
  surface-container-high: '#ede7e6'
  surface-container-highest: '#e8e1e1'
  on-surface: '#1d1b1b'
  on-surface-variant: '#4f4445'
  inverse-surface: '#333030'
  inverse-on-surface: '#f6efef'
  outline: '#807475'
  outline-variant: '#d2c3c4'
  surface-tint: '#70585b'
  primary: '#70585b'
  on-primary: '#ffffff'
  primary-container: '#fadadd'
  on-primary-container: '#765e61'
  inverse-primary: '#debfc2'
  secondary: '#5c5d6e'
  on-secondary: '#ffffff'
  secondary-container: '#e1e1f5'
  on-secondary-container: '#626374'
  tertiary: '#546256'
  on-tertiary: '#ffffff'
  tertiary-container: '#d7e6d7'
  on-tertiary-container: '#5a675c'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#fbdbde'
  primary-fixed-dim: '#debfc2'
  on-primary-fixed: '#281719'
  on-primary-fixed-variant: '#574144'
  secondary-fixed: '#e1e1f5'
  secondary-fixed-dim: '#c5c5d8'
  on-secondary-fixed: '#191b29'
  on-secondary-fixed-variant: '#444655'
  tertiary-fixed: '#d8e6d8'
  tertiary-fixed-dim: '#bccabc'
  on-tertiary-fixed: '#121e15'
  on-tertiary-fixed-variant: '#3d4a3f'
  background: '#fff8f7'
  on-background: '#1d1b1b'
  surface-variant: '#e8e1e1'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '300'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '500'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.0'
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-margin: 24px
  gutter: 16px
  section-gap: 40px
  element-gap: 12px
---

## Brand & Style

The design system is defined by a "Cyber-Ethereal" aesthetic—a sophisticated intersection of ancient mysticism and futuristic technology. It targets a female-centric audience seeking a premium, intuitive, and spiritually grounding experience. The visual language balances the weightlessness of a dream with the precision of digital data. 

The personality is ethereal, luminous, and calm. To achieve this, the system utilizes a blend of **Minimalism** and **Glassmorphism**. High amounts of whitespace create an airy feel, while translucent, frosted layers suggest depth without clutter. The "cyber" element is introduced through delicate, glowing borders and ultra-fine lines that mimic holographic interfaces, grounding the mystical subject matter in a modern, high-tech context.

## Colors

The palette is anchored by a pearl-white base with soft pink undertones, ensuring the UI feels warm rather than clinical. 

*   **Primary (Sakura Pink):** Used for call-to-action elements and key brand moments. It represents vitality and the "heart line."
*   **Secondary (Lavender):** Used for spiritual insights, wisdom, and intuitive navigation elements.
*   **Tertiary (Soft Mint):** Employed for balance, success states, and growth-related palmistry metrics (e.g., the "fate line").
*   **Neutrals:** High-contrast text uses a deep "Ink" grey rather than pure black to maintain the soft, premium feel. 

Gradients should be used sparingly, primarily as subtle angular flows between Sakura and Lavender to simulate the shifting light of a crystal.

## Typography

This design system utilizes **Plus Jakarta Sans** across all levels to maintain a contemporary, friendly, and geometric appearance. 

The typography strategy emphasizes "breathing room." Display and headline styles use lighter weights (300-500) to feel elegant and airy. Body text is set with a generous line height (1.6) to ensure long-form astrological readings remain legible and unstrained. Labels use a slightly heavier weight and increased letter spacing to provide a structural contrast to the fluid, light-weighted headlines.

## Layout & Spacing

The layout follows a **Fluid Grid** model with a soft 8px rhythmic scale. To maintain the "dreamy" feel, the design system avoids dense information clusters. 

Main containers should have wide horizontal margins (24px on mobile) to "float" the content in the center of the screen. Vertical spacing between different thematic sections should be exaggerated (40px+) to allow the user's eye to rest. Elements within a card or glass container follow a tighter 12px or 16px spacing rule to maintain internal cohesion.

## Elevation & Depth

Elevation is communicated through **Glassmorphism** and soft, tinted shadows rather than traditional grey drop shadows.

1.  **Backdrop Blur:** Primary containers use a `blur(20px)` effect with a high-transparency white fill (e.g., `rgba(255, 255, 255, 0.4)`).
2.  **Thin Borders:** Every elevated surface must have a 1px solid border. Use a semi-transparent white for the top/left edges and a soft lavender or pink for the bottom/right edges to simulate a light source.
3.  **Ambient Glow:** Instead of shadows, use "Glows." Apply a diffused `box-shadow` with the color of the accent (Sakura or Lavender) at a very low opacity (10-15%) to make components appear as if they are radiating light.

## Shapes

The shape language is organic yet controlled. 
*   **Base Radius:** Standard buttons and cards use a 1rem (16px) radius to feel approachable and soft.
*   **Interactive Elements:** Selection chips and small icons may use a pill-shape (fully rounded) to contrast against the more structural cards.
*   **Geometric Accents:** Use ultra-thin, perfect circles or celestial-inspired lines as decorative background elements to reinforce the "cyber" aspect of the palmistry readings.

## Components

*   **Glass Cards:** The primary container for readings. Features a frosted background, thin 1px border, and a subtle inner glow.
*   **Action Buttons:** Should be semi-transparent with a gradient stroke. On hover/active states, the background should saturate slightly with Sakura Pink.
*   **Mystic Chips:** Small, pill-shaped tags used for palm line categories (e.g., "Life Line"). These use the Soft Mint or Lavender accents with 20% opacity fills.
*   **Input Fields:** Ghost-style inputs with only a bottom border or a very faint all-around border. Upon focus, the border should glow with a soft Lavender light.
*   **Data Visualization:** Palm "mapping" lines should be ultra-thin (0.5pt) with glowing terminus points, mimicking a digital scan of the hand.
*   **Modals:** Full-screen blurs that settle the background content, bringing a single floating glass panel to the foreground.