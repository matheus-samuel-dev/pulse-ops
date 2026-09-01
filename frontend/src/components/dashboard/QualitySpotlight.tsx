import ScienceOutlinedIcon from '@mui/icons-material/ScienceOutlined';
import VerifiedRoundedIcon from '@mui/icons-material/VerifiedRounded';
import { alpha, Box, Chip, LinearProgress, Stack, Typography, useTheme } from '@mui/material';
import { Panel } from '../common/Panel';
import { formatPercent } from './dashboardFormatters';

const testStack = ['JUnit 5', 'Mockito', 'AssertJ', 'Testcontainers', 'JaCoCo'];

export function QualitySpotlight({ coverage }: { coverage: number }) {
  const theme = useTheme();
  const healthy = coverage >= 85;
  return (
    <Panel
      sx={{
        p: { xs: 2, md: 2.5 },
        position: 'relative',
        overflow: 'hidden',
        borderColor: alpha(theme.palette.secondary.main, 0.22),
      }}
    >
      <Box
        aria-hidden="true"
        sx={{
          position: 'absolute',
          inset: 'auto -80px -100px auto',
          width: 230,
          height: 230,
          borderRadius: '50%',
          bgcolor: alpha(theme.palette.secondary.main, 0.055),
        }}
      />
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" position="relative">
        <Box>
          <Stack direction="row" spacing={0.9} alignItems="center">
            <ScienceOutlinedIcon color="secondary" sx={{ fontSize: 20 }} />
            <Typography variant="h2">Quality First</Typography>
          </Stack>
          <Typography color="text.secondary" variant="body2" mt={0.6}>
            Confiança sustentada por uma suíte automatizada real
          </Typography>
        </Box>
        <Chip
          size="small"
          icon={<VerifiedRoundedIcon />}
          color={healthy ? 'success' : 'warning'}
          label={healthy ? 'Meta atingida' : 'Em atenção'}
          variant="outlined"
          sx={{ fontSize: '0.67rem', fontWeight: 650 }}
        />
      </Stack>
      <Stack direction="row" alignItems="baseline" spacing={1} mt={3} position="relative">
        <Typography fontSize="2.4rem" lineHeight={1} fontWeight={750} letterSpacing="-0.055em">
          {formatPercent(coverage)}
        </Typography>
        <Typography color="text.secondary" variant="body2">cobertura média</Typography>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={Math.min(100, coverage)}
        aria-label={`Cobertura média de testes: ${formatPercent(coverage)}`}
        sx={{
          mt: 2,
          height: 7,
          borderRadius: 5,
          bgcolor: alpha(theme.palette.secondary.main, 0.11),
          '& .MuiLinearProgress-bar': { bgcolor: 'secondary.main', borderRadius: 5 },
        }}
      />
      <Stack direction="row" flexWrap="wrap" gap={0.75} mt={2.5} position="relative">
        {testStack.map((item) => (
          <Chip
            key={item}
            label={item}
            size="small"
            variant="outlined"
            sx={{ height: 26, borderColor: 'divider', color: 'text.secondary', fontSize: '0.67rem' }}
          />
        ))}
      </Stack>
    </Panel>
  );
}
