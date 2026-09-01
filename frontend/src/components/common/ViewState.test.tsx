import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material';
import { describe, expect, it, vi } from 'vitest';
import { ViewState } from './ViewState';

describe('ViewState', () => {
  it('exposes accessible error feedback and retry action', async () => {
    const retry = vi.fn();
    render(<ThemeProvider theme={createTheme()}><ViewState kind="error" title="Falha" description="API indisponível" actionLabel="Tentar novamente" onAction={retry} /></ThemeProvider>);
    expect(screen.getByRole('alert')).toHaveTextContent('API indisponível');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(retry).toHaveBeenCalledOnce();
  });
});
