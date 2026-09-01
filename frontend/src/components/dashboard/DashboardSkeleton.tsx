import { Box, Skeleton, Stack } from '@mui/material';
import { Panel } from '../common/Panel';

export function DashboardSkeleton() {
  return (
    <Box aria-label="Carregando dados do dashboard" role="status">
      <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2, 1fr)', xl: 'repeat(4, 1fr)' }} gap={2}>
        {Array.from({ length: 4 }, (_, index) => (
          <Panel key={index} sx={{ p: 2.25, height: 138 }}>
            <Stack spacing={1.5}>
              <Skeleton width="45%" />
              <Skeleton width="56%" height={42} />
              <Skeleton width="70%" />
            </Stack>
          </Panel>
        ))}
      </Box>
      <Box display="grid" gridTemplateColumns={{ xs: '1fr', xl: 'minmax(0, 2fr) minmax(300px, .8fr)' }} gap={2} mt={2}>
        {[0, 1].map((item) => (
          <Panel key={item} sx={{ p: 2.5, height: 380 }}>
            <Skeleton width="30%" height={28} />
            <Skeleton variant="rounded" height={285} sx={{ mt: 2 }} />
          </Panel>
        ))}
      </Box>
      <Skeleton variant="rounded" height={310} sx={{ mt: 2 }} />
    </Box>
  );
}
