import { Box, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description: string;
  eyebrow?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, description, eyebrow, actions }: PageHeaderProps) {
  return (
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ md: 'flex-end' }} gap={2.5} mb={3}>
      <Box>
        {eyebrow && (
          <Typography variant="caption" color="primary.light" fontWeight={750} letterSpacing=".08em" textTransform="uppercase">
            {eyebrow}
          </Typography>
        )}
        <Typography variant="h1" mt={eyebrow ? 0.5 : 0}>{title}</Typography>
        <Typography color="text.secondary" mt={0.75}>{description}</Typography>
      </Box>
      {actions && <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.2}>{actions}</Stack>}
    </Stack>
  );
}
