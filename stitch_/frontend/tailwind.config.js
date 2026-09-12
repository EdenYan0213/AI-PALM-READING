import forms from '@tailwindcss/forms';
import containerQueries from '@tailwindcss/container-queries';

/** @type {import('tailwindcss').Config} */
// 全站唯一的 Tailwind token 配置（原先 87 行内联在 4 个 Stitch 页面里各复制一份）。
// token 来源：cyber_ethereal_palmistry/DESIGN.md（Material 3 风格粉绿色板）。
export default {
  darkMode: 'class',
  content: ['./*.html', './src/**/*.{js,css,html}'],
  theme: {
    extend: {
      colors: {
        'on-tertiary-container': '#5a675c',
        'on-primary-container': '#765e61',
        'on-secondary': '#ffffff',
        'on-primary-fixed': '#281719',
        'inverse-surface': '#333030',
        'primary-container': '#fadadd',
        'on-secondary-container': '#626374',
        'surface-variant': '#e8e1e1',
        'surface-container-highest': '#e8e1e1',
        'on-surface-variant': '#4f4445',
        'inverse-primary': '#debfc2',
        'surface': '#fff8f7',
        'surface-bright': '#fff8f7',
        'primary': '#70585b',
        'primary-fixed': '#fbdbde',
        'error': '#ba1a1a',
        'tertiary-fixed': '#d8e6d8',
        'on-surface': '#1d1b1b',
        'on-tertiary-fixed': '#121e15',
        'outline-variant': '#d2c3c4',
        'surface-dim': '#dfd8d8',
        'tertiary-container': '#d7e6d7',
        'surface-container-lowest': '#ffffff',
        'on-primary': '#ffffff',
        'secondary': '#5c5d6e',
        'surface-container-low': '#f9f2f2',
        'on-primary-fixed-variant': '#574144',
        'on-secondary-fixed-variant': '#444655',
        'on-tertiary-fixed-variant': '#3d4a3f',
        'surface-container': '#f3ecec',
        'inverse-on-surface': '#f6efef',
        'error-container': '#ffdad6',
        'on-background': '#1d1b1b',
        'on-tertiary': '#ffffff',
        'surface-container-high': '#ede7e6',
        'tertiary': '#546256',
        'outline': '#807475',
        'on-secondary-fixed': '#191b29',
        'secondary-fixed-dim': '#c5c5d8',
        'tertiary-fixed-dim': '#bccabc',
        'on-error-container': '#93000a',
        'surface-tint': '#70585b',
        'primary-fixed-dim': '#debfc2',
        'secondary-fixed': '#e1e1f5',
        'background': '#fff8f7',
        'secondary-container': '#e1e1f5',
        'on-error': '#ffffff'
      },
      borderRadius: {
        DEFAULT: '0.25rem',
        lg: '0.5rem',
        xl: '0.75rem',
        full: '9999px'
      },
      spacing: {
        'container-margin': '24px',
        'element-gap': '12px',
        gutter: '16px',
        'section-gap': '40px',
        unit: '8px'
      },
      fontFamily: {
        'headline-md': ['Plus Jakarta Sans'],
        'headline-lg': ['Plus Jakarta Sans'],
        'body-lg': ['Plus Jakarta Sans'],
        'display-lg': ['Plus Jakarta Sans'],
        'body-md': ['Plus Jakarta Sans'],
        'label-md': ['Plus Jakarta Sans']
      },
      fontSize: {
        'headline-md': ['24px', { lineHeight: '1.3', fontWeight: '500' }],
        'headline-lg': ['32px', { lineHeight: '1.2', letterSpacing: '-0.01em', fontWeight: '500' }],
        'body-lg': ['18px', { lineHeight: '1.6', fontWeight: '400' }],
        'display-lg': ['48px', { lineHeight: '1.1', letterSpacing: '-0.02em', fontWeight: '300' }],
        'body-md': ['16px', { lineHeight: '1.6', fontWeight: '400' }],
        'label-md': ['14px', { lineHeight: '1.0', letterSpacing: '0.05em', fontWeight: '600' }]
      }
    }
  },
  plugins: [forms, containerQueries]
};
