import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { alpha, createTheme, CssBaseline, ThemeProvider, type PaletteMode } from '@mui/material';

interface ColorModeContextValue {
  mode: PaletteMode;
  toggleColorMode: () => void;
}

const ColorModeContext = createContext<ColorModeContextValue | null>(null);
const MODE_KEY = 'pulseops.color-mode';

function initialMode(): PaletteMode {
  const stored = window.localStorage.getItem(MODE_KEY);
  return stored === 'light' ? 'light' : 'dark';
}

function buildTheme(mode: PaletteMode) {
  const dark = mode === 'dark';
  const background = dark ? '#07101f' : '#f3f6fb';
  const paper = dark ? '#0d182a' : '#ffffff';
  const textPrimary = dark ? '#f2f6fc' : '#142036';
  const textSecondary = dark ? '#8fa0b9' : '#5d6b82';

  return createTheme({
    palette: {
      mode,
      primary: { main: '#5b8cff', light: '#85aaff', dark: '#3f6ed5' },
      secondary: { main: '#9d7bff' },
      success: { main: '#39c995' },
      warning: { main: '#f2b84b' },
      error: { main: '#ef6673' },
      info: { main: '#56b6f7' },
      background: { default: background, paper },
      text: { primary: textPrimary, secondary: textSecondary },
      divider: dark ? 'rgba(148, 163, 184, 0.13)' : 'rgba(26, 42, 68, 0.11)',
    },
    typography: {
      fontFamily: 'Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
      h1: { fontSize: '2rem', fontWeight: 700, letterSpacing: '-0.04em', lineHeight: 1.2 },
      h2: { fontSize: '1.25rem', fontWeight: 650, letterSpacing: '-0.025em' },
      h3: { fontSize: '1rem', fontWeight: 650 },
      button: { fontWeight: 650, textTransform: 'none', letterSpacing: '-0.01em' },
      body1: { fontSize: '0.95rem' },
      body2: { fontSize: '0.85rem' },
      caption: { fontSize: '0.75rem', letterSpacing: '0.012em' },
    },
    shape: { borderRadius: 12 },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          html: { minHeight: '100%', backgroundColor: background },
          body: { minHeight: '100%', backgroundColor: background },
          '#root': { minHeight: '100vh' },
          '*': { boxSizing: 'border-box' },
          '*:focus-visible': { outline: '2px solid #85aaff', outlineOffset: 2 },
          '::selection': { background: alpha('#5b8cff', 0.35) },
          '*::-webkit-scrollbar': { width: 8, height: 8 },
          '*::-webkit-scrollbar-thumb': {
            background: dark ? 'rgba(143, 160, 185, .28)' : 'rgba(93, 107, 130, .25)',
            borderRadius: 8,
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { minHeight: 38, borderRadius: 10 },
          containedPrimary: {
            boxShadow: `0 8px 24px ${alpha('#5b8cff', dark ? 0.2 : 0.16)}`,
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 10,
            backgroundColor: dark ? 'rgba(255,255,255,0.018)' : '#fff',
          },
        },
      },
      MuiTooltip: {
        defaultProps: { arrow: true },
        styleOverrides: {
          tooltip: {
            backgroundColor: dark ? '#18253a' : '#1c2b42',
            fontSize: '0.72rem',
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            margin: 16,
            maxHeight: 'calc(100% - 32px)',
          },
        },
      },
    },
  });
}

export function PulseOpsThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<PaletteMode>(initialMode);
  const theme = useMemo(() => buildTheme(mode), [mode]);
  const context = useMemo(
    () => ({
      mode,
      toggleColorMode: () =>
        setMode((current) => {
          const next = current === 'dark' ? 'light' : 'dark';
          window.localStorage.setItem(MODE_KEY, next);
          return next;
        }),
    }),
    [mode],
  );

  return (
    <ColorModeContext.Provider value={context}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode() {
  const value = useContext(ColorModeContext);
  if (!value) throw new Error('useColorMode deve ser usado dentro de PulseOpsThemeProvider.');
  return value;
}
