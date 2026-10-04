import { alpha, Paper, type PaperProps } from '@mui/material';

export function Panel({ sx, ...props }: PaperProps) {
  return (
    <Paper
      variant="outlined"
      {...props}
      sx={[
        (theme) => ({
          minWidth: 0,
          borderColor: 'divider',
          borderRadius: 3,
          backgroundColor: theme.palette.background.paper,
          boxShadow:
            theme.palette.mode === 'dark'
              ? '0 10px 28px rgba(0, 0, 0, .13)'
              : '0 12px 28px rgba(36, 53, 77, .055)',
          transition: 'border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease',
          '&:hover': { borderColor: alpha(theme.palette.primary.main, 0.22) },
        }),
        ...(sx ? (Array.isArray(sx) ? sx : [sx]) : []),
      ]}
    />
  );
}
