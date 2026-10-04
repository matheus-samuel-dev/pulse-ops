import AutoGraphRoundedIcon from '@mui/icons-material/AutoGraphRounded';
import { Box, Stack, Typography, type SxProps, type Theme } from '@mui/material';

interface PulseOpsLogoProps {
  compact?: boolean;
  sx?: SxProps<Theme>;
}

export function PulseOpsLogo({ compact = false, sx }: PulseOpsLogoProps) {
  return (
    <Stack direction="row" spacing={1.25} alignItems="center" sx={sx}>
      <Box
        aria-hidden="true"
        sx={{
          width: 38,
          height: 38,
          borderRadius: 2.5,
          display: 'grid',
          placeItems: 'center',
          color: '#fff',
          background: 'linear-gradient(145deg, #5b8cff 12%, #756ae7 88%)',
          boxShadow: '0 10px 25px rgba(91,140,255,.25)',
        }}
      >
        <AutoGraphRoundedIcon fontSize="small" />
      </Box>
      {!compact && (
        <Box>
          <Typography fontSize="1.02rem" lineHeight={1.15} fontWeight={750} letterSpacing="-0.035em">
            PulseOps
          </Typography>
          <Typography color="text.secondary" fontSize="0.62rem" letterSpacing="0.07em" textTransform="uppercase">
            Central de operações
          </Typography>
        </Box>
      )}
    </Stack>
  );
}
