import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';
import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded';

interface Props { children: ReactNode }
interface State { hasError: boolean }

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) console.error('Falha não recuperável na interface do PulseOps', error, info);
  }

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <Box minHeight="100vh" display="grid" sx={{ placeItems: 'center' }} p={3}>
        <Stack alignItems="center" textAlign="center" spacing={1.5} maxWidth={450}>
          <ErrorOutlineRoundedIcon color="error" sx={{ fontSize: 44 }} />
          <Typography variant="h2">A interface encontrou um problema</Typography>
          <Typography color="text.secondary">
            Seus dados continuam seguros. Recarregue a página para retomar a operação.
          </Typography>
          <Button variant="contained" onClick={() => window.location.reload()}>
            Recarregar PulseOps
          </Button>
        </Stack>
      </Box>
    );
  }
}
