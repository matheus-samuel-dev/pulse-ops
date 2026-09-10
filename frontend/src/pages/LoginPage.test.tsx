import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

const signIn = vi.hoisted(() => vi.fn(async () => undefined));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: null,
    isAuthenticated: false,
    isBootstrapping: false,
    signIn,
    signOut: vi.fn(),
  }),
}));

describe('LoginPage', () => {
  beforeEach(() => signIn.mockClear());

  it('fills the documented demo credentials', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider theme={createTheme()}>
          <LoginPage />
        </ThemeProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'Preencher' }));

    expect(screen.getByLabelText('E-mail')).toHaveValue('admin@pulseops.dev');
    expect(screen.getByLabelText('Senha')).toHaveValue('PulseOps@2026');
    expect(signIn).not.toHaveBeenCalled();
  });
});
