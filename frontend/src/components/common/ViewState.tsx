import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import { Box, Button, Stack, Typography } from '@mui/material';

interface ViewStateProps {
  kind: 'error' | 'empty';
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function ViewState({ kind, title, description, actionLabel, onAction }: ViewStateProps) {
  const Icon = kind === 'error' ? ErrorOutlineRoundedIcon : InboxOutlinedIcon;
  return (
    <Box py={8} px={3} textAlign="center" role={kind === 'error' ? 'alert' : 'status'}>
      <Stack alignItems="center" spacing={1.25}>
        <Box
          sx={{
            width: 44,
            height: 44,
            display: 'grid',
            placeItems: 'center',
            borderRadius: 2.5,
            color: kind === 'error' ? 'error.main' : 'text.secondary',
            bgcolor: kind === 'error' ? 'rgba(239,102,115,.09)' : 'action.hover',
          }}
        >
          <Icon />
        </Box>
        <Typography variant="h3">{title}</Typography>
        <Typography color="text.secondary" maxWidth={430}>
          {description}
        </Typography>
        {actionLabel && onAction && (
          <Button variant="outlined" onClick={onAction} sx={{ mt: 1 }}>
            {actionLabel}
          </Button>
        )}
      </Stack>
    </Box>
  );
}
